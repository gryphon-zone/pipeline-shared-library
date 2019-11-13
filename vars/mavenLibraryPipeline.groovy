/*
 * Copyright 2019-2019 Gryphon Zone
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


import zone.gryphon.pipeline.configuration.ConfigurationHelper
import zone.gryphon.pipeline.configuration.MavenLibraryPipelineConfiguration
import zone.gryphon.pipeline.configuration.effective.EffectiveMavenLibraryPipelineConfiguration
import zone.gryphon.pipeline.model.CheckoutInformation
import zone.gryphon.pipeline.model.JobInformation
import zone.gryphon.pipeline.toolbox.ScopeUtility
import zone.gryphon.pipeline.toolbox.Util

@SuppressWarnings("GrMethodMayBeStatic")
private def performRelease(final Util util, final String releaseVersion, final String mavenOpts) {
    final ScopeUtility scope = new ScopeUtility()

    // maven command. Note that parameters and goals aren't configurable, since we should have already performed
    // CI build prior to calling release
    final String mvn = "MAVEN_OPTS='${mavenOpts}' mvn -B -V -Dstyle.color=always -DskipTests=true"

    final String commonPrepareArguments = """\
        -DpreparationGoals='validate' \
        -Darguments='-Dstyle.color=always' \
        -DremoteTagging=false \
        -DpushChanges=false \
        -Dresume=false \
        """.stripIndent().trim()

    sshagent(['github-ssh']) {
        util.sh('mkdir -p ~/.ssh && echo StrictHostKeyChecking no > ~/.ssh/config', quiet: true)
        util.configureGitCommitter()

        util.sh("""\
            ${mvn} \
                release:prepare \
                ${commonPrepareArguments} \
                -Dtag='${releaseVersion}' \
                -DsuppressCommitBeforeTag=true \
                -DupdateWorkingCopyVersions=false \
                """.stripIndent(), returnType: 'none')

        util.sh("""\
            ${mvn} \
                release:prepare \
                ${commonPrepareArguments} \
                -DreleaseVersion='${releaseVersion}' \
                -DdevelopmentVersion='${releaseVersion}-mvn-release-SNAPSHOT' \
                """.stripIndent(), returnType: 'none')

        scope.withGpgKey('gpg-signing-key-id', 'gpg-signing-key', 'GPG_KEYID') {
            withCredentials([usernamePassword(credentialsId: 'ossrh', usernameVariable: 'OSSRH_USERNAME', passwordVariable: 'OSSRH_PASSWORD')]) {

                // push artifacts
                util.sh("""\
                    ${mvn} \
                        release:perform \
                        -Darguments='-Dstyle.color=always -DskipTests=true' \
                        -DlocalCheckout='true' \
                        -Dossrh.username='${OSSRH_USERNAME}' \
                        -Dossrh.password='${OSSRH_PASSWORD}'
                        """.stripIndent(), returnType: 'none')
            }
        }

        // push release tag to remote
        util.sh("git push origin '${releaseVersion}'", returnType: 'none')
    }
}

@SuppressWarnings("GrMethodMayBeStatic")
private def performBuild(final EffectiveMavenLibraryPipelineConfiguration config, final Util util, String mavenOpts) {
    try {
        util.sh("MAVEN_OPTS=\"${mavenOpts}\" mvn ${config.arguments}", returnType: 'none')
    } finally {
        junit(allowEmptyResults: true, testResults: config.junitResultsPattern)

        try {
            jacoco(execPattern: config.jacocoResultsPattern)
        } catch (Exception e) {
            log.error("Failed to save Jacoco results: ${e.message}")
        }
    }
}

private def build(final EffectiveMavenLibraryPipelineConfiguration config) {
    final String mavenOpts = '-Djansi.force=true'
    final Util util = new Util()
    final JobInformation info = util.getJobInformation()

    // set up global maven settings
    util.installMavenSettingsFile()

    // generates version tag in the form <pom>.<build>-<commit>
    // assuming poms use major.minor versioning, will produce versions like 1.2.3-asdfdef
    log.info('Calculating build version...')
    String mavenProjectVersion = util.determineMavenProjectVersion().replace('-SNAPSHOT', '')
    final String version = "${mavenProjectVersion}.${info.build}-${Util.shortHash(config.checkoutInformation)}"
    log.info("Build version calculated to be \"${version}\"")

    currentBuild.displayName = "${version} (#${info.build})"
    currentBuild.description = config.release ? 'Release Project' : 'Build Project'

    stage('Maven Dependency Logging') {

        log.info('Logging Maven project dependencies...')

        try {
            String dependencyFile = "maven-dependency-tree.txt"
            util.sh("MAVEN_OPTS='${mavenOpts}' mvn -B -V -q -Dstyle.color=always dependency:tree -DoutputFile='${dependencyFile}' || true", returnType: 'none')
            archiveArtifacts(allowEmptyArchive: true, artifacts: dependencyFile)
            util.sh("rm -f '${dependencyFile}'", returnType: 'none', quiet: true)
        } catch (Exception e) {
            log.warn("Failed to calculate project dependencies: ${e}")
            unstable('Failed to calculate project dependencies')
        }
    }

    stage('Maven Build') {

        log.info("Running maven build with arguments \"${config.arguments}\"...")

        performBuild(config, util, mavenOpts)
    }

    if (config.release) {
        stage('Maven release') {

            log.info("Performing maven release...")

            // note: maven arguments for release intentionally aren't configurable
            performRelease(util, version, mavenOpts)
        }
    }
}

EffectiveMavenLibraryPipelineConfiguration parseConfiguration(
        String organization,
        CheckoutInformation checkoutInformation,
        Closure body) {

    final Util util = new Util()
    final EffectiveMavenLibraryPipelineConfiguration out = new EffectiveMavenLibraryPipelineConfiguration()
    final ConfigurationHelper helper = new ConfigurationHelper()
    final JobInformation info = util.getJobInformation()
    final MavenLibraryPipelineConfiguration config = helper.configure(organization, body, new MavenLibraryPipelineConfiguration())
    final boolean deployable = helper.isDeployable(config, info)
    final List buildParameters = []

    buildParameters.add(stringParam(
            defaultValue: deployable ? config.mavenDeployArguments : config.mavenNonDeployArguments,
            description: 'The arguments for the Maven CI build',
            name: 'arguments'
    ))

    if (deployable) {
        buildParameters.add(
                booleanParam(
                        defaultValue: config.automaticallyRelease,
                        description: 'Whether or not to release the Maven artifacts to Nexus Central',
                        name: 'release'
                )
        )
    }

    List calculatedJobProperties = helper.calculateAndAssignJobProperties(config, (Object) parameters(buildParameters))

    if (util.buildWasTriggerByCommit()) {
        // SCM change triggered build, use the parameter definitions from the configuration
        out.release = deployable && "${config.automaticallyRelease}".trim().toBoolean()
        out.arguments = out.release ? config.mavenDeployArguments : config.mavenNonDeployArguments
    } else {
        // manual build, use the values passed in the parameters
        out.release = deployable && "${params.release}".trim().toBoolean()
        out.arguments = "${params.arguments}"
    }

    out.buildAgent = config.buildAgent
    out.timeoutMinutes = config.idleTimeout
    out.junitResultsPattern = config.junitResultsPattern
    out.jacocoResultsPattern = config.jacocoResultsPattern
    out.checkoutInformation = checkoutInformation

    helper.printConfiguration([
            'Deployable branches'    : config.deployableBranchRegex,
            'Deployable organization': organization,
            'SCM organization'       : info.organization,
            'SCM repository'         : info.repository,
            'SCM branch'             : info.branch,
            'Is job deployable'      : deployable,
            'Build agent'            : out.buildAgent,
            'Maven build arguments'  : out.arguments,
            'Perform Maven release'  : out.release,
            'JUnit reports directory': out.junitResultsPattern,
            'Jacoco coverage file'   : out.jacocoResultsPattern,
            'Job properties'         : helper.convertPropertiesToPrintableForm(calculatedJobProperties)
    ])

    return out
}

def call(String githubOrganization, Closure body) {
    final EffectiveMavenLibraryPipelineConfiguration configuration
    final ScopeUtility scope = new ScopeUtility()

    // add standard pipeline wrappers.
    // this command also allocates a build agent for running the build
    scope.withStandardPipelineWrappers { CheckoutInformation checkoutInformation ->

        stage('Configure Project') {

            if (!fileExists('pom.xml')) {
                String message = 'pom.xml not present in project root'
                log.error(message)
                unstable(message)
                currentBuild.description = message
                return
            }

            configuration = parseConfiguration(githubOrganization, checkoutInformation, body)
        }

        // null configuration means project is invalid and we should abort the build
        if (configuration == null) {
            return
        }

        // kill build if it goes longer than a given number of minutes without logging anything
        scope.withTimeout(configuration.timeoutMinutes) {

            // TODO: dynamically generate cache location
            String dockerArgs = """\
                            -v jenkins-shared-m2-cache:'/root/.m2/repository'
                            """.stripIndent().replace("\r\n", "")

            // run build inside of docker build image
            scope.inDockerImage(configuration.buildAgent, args: dockerArgs) {

                build(configuration)

            }
        }
    }
}
