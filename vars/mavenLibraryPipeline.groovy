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

/**
 * Reads the current version of the maven POM in the current directory
 */
@SuppressWarnings("GrMethodMayBeStatic")
private String readMavenVersion(final Util util) {
    return ((String) util.sh("mvn help:evaluate -Dexpression=project.version 2>/dev/null | sed -n -e '/^\\[.*\\]/ !{ /^[0-9]/ { p; q } }'", quiet: true))
            .replace('\r\n', '')
            .trim()
}

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
    sshagent(['github-ssh']) {
        util.sh('mkdir -p ~/.ssh && echo StrictHostKeyChecking no > ~/.ssh/config', quiet: true)
        util.sh("git push origin '${releaseVersion}'", returnType: 'none')
    }
}

@SuppressWarnings("GrMethodMayBeStatic")
private def performBuild(final EffectiveMavenLibraryPipelineConfiguration config, final Util util, String mavenOpts) {
    try {
        util.sh("MAVEN_OPTS=\"${mavenOpts}\" mvn ${config.mavenArguments}", returnType: 'none')
    } finally {
        junit(allowEmptyResults: true, testResults: config.junitResultsPattern)
    }
}

private def build(final EffectiveMavenLibraryPipelineConfiguration config) {
    final Util util = new Util()
    final JobInformation info = util.getJobInformation()
    boolean abort = false
    CheckoutInformation checkoutInformation
    String version
    String mavenOpts

    stage('Checkout Project') {

        // enable git color before performing checkout
        util.enableGitColor()

        checkoutInformation = util.checkoutProject()
    }

    stage('Configure Project') {

        // generates version tag in the form <pom>.<build>-<commit>
        // assuming poms use major.minor versioning, will produce versions like 1.2.3-asdfdef
        // NOTE: if there is no maven pom present, `readMavenVersion()` returns "1"
        log.info('Calculating build version')
        version = "${readMavenVersion(util).replace('-SNAPSHOT', '')}.${info.build}-${checkoutInformation.gitCommit.substring(0, 7)}"

        currentBuild.displayName = "${version} (#${info.build})"
        currentBuild.description = config.performRelease ? 'Release Project' : 'Build Project'

        log.debug('Ensuring maven POM exists')
        if (!fileExists('pom.xml')) {
            log.error('no pom.xml found, aborting build')
            unstable('pom.xml not present in project root')
            abort = true
            return
        }

        // needed to prevent failures when attempting to make commits
        log.info('Configuring git author information')
        util.sh("""\
            git config user.email '${checkoutInformation.gitAuthorEmail}' && \
            git config user.name '${checkoutInformation.gitAuthorName}'
            """.stripIndent().trim(), returnType: 'none')

        // set up global maven settings
        util.configureMavenSettingsFile()

        log.info('Calculating \'$MAVEN_OPTS\' variable')
        mavenOpts = (util.sh('echo -n $MAVEN_OPTS', quiet: true) + ' -Djansi.force=true').trim()
    }

    if (abort) {
        return
    }

    stage('Maven Dependency Logging') {

        log.info('Logging Maven project dependencies')

        try {
            util.sh("MAVEN_OPTS='${mavenOpts}' mvn -B -V -Dstyle.color=always dependency:tree", returnType: 'none')
        } catch (Exception e) {
            log.warn("Failed to calculate project dependencies: ${e}")
            unstable('Failed to calculate project dependencies')
        }
    }

    stage('Maven Build') {

        log.info("Running maven build with arguments \"${config.mavenArguments}\"")

        performBuild(config, util, mavenOpts)
    }

    if (config.performRelease) {
        stage('Maven release') {

            log.info("Performing maven release")

            // note: maven arguments for release intentionally aren't configurable
            performRelease(util, version, mavenOpts)
        }
    }
}

EffectiveMavenLibraryPipelineConfiguration parseConfiguration(String organization, Closure body) {
    final Util util = new Util()

    final EffectiveMavenLibraryPipelineConfiguration finalConfig = new EffectiveMavenLibraryPipelineConfiguration()
    final ConfigurationHelper helper = new ConfigurationHelper()
    final JobInformation info = util.getJobInformation()

    MavenLibraryPipelineConfiguration config = helper.configure(organization, body, new MavenLibraryPipelineConfiguration())

    boolean deployable = helper.isDeployable(config, info)

    String defaultMavenArgs
    List buildParameters = []

    if (deployable) {
        buildParameters.add(
                booleanParam(
                        defaultValue: config.automaticallyRelease,
                        description: 'Whether or not to release the maven artifacts',
                        name: 'performRelease'
                )
        )

        defaultMavenArgs = config.mavenDeployArguments
    } else {
        defaultMavenArgs = config.mavenNonDeployArguments
    }

    buildParameters.add(0, stringParam(
            defaultValue: defaultMavenArgs,
            description: 'Maven build arguments',
            name: 'mavenArguments'
    ))

    List calculatedJobProperties = helper.calculateProperties(config, (Object) parameters(buildParameters))

    // set job properties
    //noinspection GroovyAssignabilityCheck
    properties(calculatedJobProperties)

    finalConfig.buildAgent = config.buildAgent
    finalConfig.timeoutMinutes = config.idleTimeout
    finalConfig.junitResultsPattern = config.junitResultsPattern

    if (util.buildWasTriggerByCommit()) {
        // SCM change triggered build, use the parameter definitions from the configuration
        finalConfig.mavenArguments = finalConfig.performRelease ? config.mavenDeployArguments : config.mavenNonDeployArguments
        finalConfig.performRelease = deployable && "${config.automaticallyRelease}".trim().toBoolean()
    } else {
        // manual build, use the values passed in the parameters
        finalConfig.mavenArguments = "${params.mavenArguments}"
        finalConfig.performRelease = deployable && "${params.performRelease}".trim().toBoolean()
    }

    helper.printConfiguration([
            'Deployable branches'    : config.deployableBranchRegex,
            'Deployable organization': organization,
            'SCM organization'       : info.organization,
            'SCM repository'         : info.repository,
            'SCM branch'             : info.branch,
            'Job is deployable'      : deployable,
            'Build agent'            : finalConfig.buildAgent,
            'Maven build arguments'  : finalConfig.mavenArguments,
            'Perform Maven release'  : finalConfig.performRelease,
            'JUnit reports directory': finalConfig.junitResultsPattern,
            'Job properties'         : helper.convertPropertiesToPrintableForm(calculatedJobProperties)
    ])

    return finalConfig
}

def call(String githubOrganization, Closure body) {
    final EffectiveMavenLibraryPipelineConfiguration configuration
    final ScopeUtility scope = new ScopeUtility()

    // add standard pipeline wrappers.
    // this command also allocates a build agent for running the build
    scope.withStandardPipelineWrappers {

        stage('Parse Configuration') {
            configuration = parseConfiguration(githubOrganization, body)
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
