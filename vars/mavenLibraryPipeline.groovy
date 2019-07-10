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
import zone.gryphon.pipeline.configuration.parsed.ParsedMavenLibraryPipelineConfiguration
import zone.gryphon.pipeline.model.CheckoutInformation
import zone.gryphon.pipeline.model.JobInformation
import zone.gryphon.pipeline.toolbox.ScopeUtility
import zone.gryphon.pipeline.toolbox.TextColor
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
private def performBuild(final ParsedMavenLibraryPipelineConfiguration config, final Util util, String mavenOpts) {
    util.sh("MAVEN_OPTS=\"${mavenOpts}\" mvn ${config.mavenArguments}", returnType: 'none')
}

private def build(final ParsedMavenLibraryPipelineConfiguration config) {
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
        echo('Calculating build version')
        version = "${readMavenVersion(util).replace('-SNAPSHOT', '')}.${info.build}-${checkoutInformation.gitCommit.substring(0, 7)}"

        currentBuild.displayName = "${version} (#${info.build})"
        currentBuild.description = config.performRelease ? 'Release Project' : 'Build Project'

        echo('Ensuring maven POM exists')
        if (!fileExists('pom.xml')) {
            echo('warning: no pom.xml found, aborting build')
            currentBuild.result = 'UNSTABLE'
            currentBuild.description = 'pom.xml not present in project root'
            abort = true
            return
        }

        // needed to prevent failures when attempting to make commits
        echo('Configuring git')
        util.sh("""\
            git config user.email '${checkoutInformation.gitAuthorEmail}' && \
            git config user.name '${checkoutInformation.gitAuthorName}'
            """.stripIndent().trim(), returnType: 'none')

        // set up global maven settings
        util.configureMavenSettingsFile()

        echo('Calculating \'$MAVEN_OPTS\' variable')
        mavenOpts = (util.sh('echo -n $MAVEN_OPTS', quiet: true) + ' -Djansi.force=true').trim()
    }

    if (abort) {
        return
    }

    stage('Maven Dependency Logging') {
        try {
            util.sh("MAVEN_OPTS='${mavenOpts}' mvn -B -V -Dstyle.color=always dependency:tree", returnType: 'none')
        } catch (Exception e) {
            echo("Faield to calculate project dependencies: ${e}")
            currentBuild.result = 'UNSTABLE'
        }
    }

    stage('Maven Build') {
        performBuild(config, util, mavenOpts)
    }

    if (config.performRelease) {
        stage('Maven release') {
            // note: maven arguments for release intentionally aren't configurable
            performRelease(util, version, mavenOpts)
        }
    }
}

ParsedMavenLibraryPipelineConfiguration parseConfiguration(String githubOrganization, Closure body) {
    final Util util = new Util()
    final TextColor c = TextColor.instance

    final ParsedMavenLibraryPipelineConfiguration finalConfig = new ParsedMavenLibraryPipelineConfiguration()
    final ConfigurationHelper helper = new ConfigurationHelper()
    final JobInformation info = util.getJobInformation()

    MavenLibraryPipelineConfiguration config = helper.configure(body, new MavenLibraryPipelineConfiguration())

    // branch is deployable if it matches the regex AND it's not from a fork
    boolean deployable = (githubOrganization == info.organization) && info.branch.matches(config.deployableBranchRegex)

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

    List calculatedJobProperties = helper.calculateProperties(config.jobProperties, (Object) parameters(buildParameters))

    // set job properties
    //noinspection GroovyAssignabilityCheck
    properties(calculatedJobProperties)

    finalConfig.buildAgent = config.buildAgent
    finalConfig.timeoutMinutes = config.timeoutMinutes

    if (util.buildWasTriggerByCommit()) {
        // SCM change triggered build, use the parameter definitions from the configuration
        finalConfig.mavenArguments = finalConfig.performRelease ? config.mavenDeployArguments : config.mavenNonDeployArguments
        finalConfig.performRelease = deployable && "${config.automaticallyRelease}".trim().toBoolean()
    } else {
        // manual build, use the values passed in the parameters
        finalConfig.mavenArguments = "${params.mavenArguments}"
        finalConfig.performRelease = deployable && "${params.performRelease}".trim().toBoolean()
    }

    String configurationMessage = ''
    configurationMessage += c.cyan('-' * 60) + '\n'
    configurationMessage += c.cyan('Effective Configuration:') + '\n'
    configurationMessage += c.cyan('-----------------------') + '\n'
    configurationMessage += c.green('Docker build agent   : ') + c.blue(String.valueOf(finalConfig.buildAgent)) + '\n'
    configurationMessage += c.green('Maven build arguments: ') + c.blue(String.valueOf(finalConfig.mavenArguments)) + '\n'
    configurationMessage += c.green('Perform Maven release: ') + c.blue(String.valueOf(finalConfig.performRelease)) + '\n'
    configurationMessage += c.green('Job properties:        ') + '\n'
    configurationMessage += helper.toPrintableForm(calculatedJobProperties).split("\n").collect {c.blue(it)}.join("\n") + '\n'
    configurationMessage += c.cyan('-' * 60) + '\n'

    echo(configurationMessage)

    return finalConfig
}

def call(String githubOrganization, Closure body) {

    // only call outside of timestamp block is creation of ScopeUtility,
    // all other calls in the pipeline should happen inside of the timestamp block
    final ScopeUtility scope = new ScopeUtility()
    ParsedMavenLibraryPipelineConfiguration configuration

    // add support for ANSI color
    scope.withColor {

        // add timestamps to build logs
        scope.withTimestamps {

            // no build is allowed to run for more than 1 hour
            scope.withAbsoluteTimeout(60) {

                // run all commands inside docker agent
                scope.withExecutor('docker') {

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
        }
    }
}
