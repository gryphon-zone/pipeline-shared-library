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
import zone.gryphon.pipeline.toolbox.Util

@SuppressWarnings("GrMethodMayBeStatic")
private String readMavenReleaseTag(final Util util) {
    return util.sh("grep 'scm.tag=' < release.properties | sed -E 's/^scm\\.tag=(.*)\$/\\1/g'", quiet: true)
            .replace("\r\n", "")
            .trim()
}

@SuppressWarnings("GrMethodMayBeStatic")
private String readMavenVersion(final Util util) {
    return util.sh("mvn help:evaluate -Dexpression=project.version 2>/dev/null | sed -n -e '/^\\[.*\\]/ !{ /^[0-9]/ { p; q } }'", quiet: true)
}

@SuppressWarnings("GrMethodMayBeStatic")
private def performRelease(final ParsedMavenLibraryPipelineConfiguration config, final Util util, final String releaseVersion, String mavenOpts) {
    final ScopeUtility scope = new ScopeUtility()

    util.sh("""\
        MAVEN_OPTS='${mavenOpts}' mvn ${config.mavenArguments} \
            release:prepare \
            -Darguments='-Dstyle.color=always' \
            -DpushChanges=false \
            -DpreparationGoals='validate' \
            -DremoteTagging=false \
            -Dtag='${releaseVersion}' \
            -DsuppressCommitBeforeTag=true \
            -DupdateWorkingCopyVersions=false \
            -Dresume=false
            """.stripIndent(), returnType: 'none')

    util.sh("""\
        MAVEN_OPTS='${mavenOpts}' mvn ${config.mavenArguments} \
            release:prepare \
            -DpreparationGoals='clean verify' \
            -Darguments='-Dstyle.color=always' \
            -DreleaseVersion='${releaseVersion}' \
            -DdevelopmentVersion='${releaseVersion}-mvn-release-SNAPSHOT' \
            -DpushChanges=false \
            -DremoteTagging=false \
            -Dresume=false \
            """.stripIndent(), returnType: 'none')

    String gitBuildTag = readMavenReleaseTag(util)

    try {
        scope.withGpgKey('gpg-signing-key-id', 'gpg-signing-key', 'GPG_KEYID') {
            withCredentials([usernamePassword(credentialsId: 'ossrh', usernameVariable: 'OSSRH_USERNAME', passwordVariable: 'OSSRH_PASSWORD')]) {

                // push artifacts
                util.sh("""\
                    MAVEN_OPTS='${mavenOpts}' mvn -B -V -Dstyle.color=always \
                        release:perform \
                        -Darguments='-Dstyle.color=always' \
                        -DlocalCheckout='true' \
                        -Dossrh.username='${OSSRH_USERNAME}' \
                        -Dossrh.password='${OSSRH_PASSWORD}'
                        """.stripIndent(), returnType: 'none')
            }

            // can delete tag now that code was released
            util.sh("git tag --delete '${gitBuildTag}'", quiet: true)

            // push release tag to remote
            sshagent(['github-ssh']) {
                util.sh('mkdir -p ~/.ssh && echo StrictHostKeyChecking no > ~/.ssh/config', quiet: true)
                util.sh("git push origin '${releaseVersion}'", returnType: 'none')
            }
        }
    } finally {

        // remove GPG keys
        util.sh('rm -rf ${HOME}/.gnupg', returnType: 'none')
    }
}

@SuppressWarnings("GrMethodMayBeStatic")
private def performBuild(final ParsedMavenLibraryPipelineConfiguration config, final Util util, String mavenOpts) {
    util.sh("MAVEN_OPTS=\"${mavenOpts}\" mvn clean verify ${config.mavenArguments}", returnType: 'none')
}

private def build(final ParsedMavenLibraryPipelineConfiguration config, final Util util) {
    final JobInformation info = util.getJobInformation()
    CheckoutInformation checkoutInformation

    stage('Checkout Project') {
        checkoutInformation = util.checkoutProject()

        // needed to prevent failures when attempting to make commits
        util.sh("git config user.email '${checkoutInformation.gitAuthorEmail}'")
        util.sh("git config user.name '${checkoutInformation.gitAuthorName}'")
    }

    util.setupMavenConfiguration()

    final String mavenOpts = (util.sh('echo $MAVEN_OPTS', quiet: true) + ' -Djansi.force=true').trim()

    final String version = "${readMavenVersion().replace('-SNAPSHOT', '')}.${info.build}-${checkoutInformation.gitCommit.substring(0, 7)}"

    currentBuild.displayName = "${version} (#${info.build})"
//    currentBuild.description = "Image tagged with ${String.join(', ', tags)}"

    if (config.performRelease) {
        stage('Perform Maven release') {
            performRelease(config, util, version, mavenOpts)
        }
    } else {
        stage('Perform Maven Build') {
            performBuild(config, util, mavenOpts)
        }
    }
}

def call(String githubOrganization, Closure body) {

    // only call outside of timestamp block is creation of ScopeUtility,
    // all other calls in the pipeline should happen inside of the timestamp block
    final ScopeUtility scope = new ScopeUtility()

    scope.withTimestamps {

        // add support for ANSI color
        scope.withColor {

            // no build is allowed to run for more than 1 hour
            scope.withAbsoluteTimeout(60) {

                // run all commands inside docker agent
                scope.withExecutor('docker') {

                    final ParsedMavenLibraryPipelineConfiguration parsedConfiguration = new ParsedMavenLibraryPipelineConfiguration()
                    final Util util = new Util()
                    final ConfigurationHelper helper = new ConfigurationHelper()
                    final JobInformation info = util.getJobInformation()
                    boolean wasTriggerByScm = util.buildWasTriggerByCommit()


                    MavenLibraryPipelineConfiguration config
                    List calculatedJobProperties
                    boolean deployable

                    stage('Parse Configuration') {
                        config = helper.configure(body, new MavenLibraryPipelineConfiguration())

                        // branch is deployable if it matches the regex AND it's not from a fork
                        deployable = (githubOrganization == info.organization) && info.branch.matches(config.deployableBranchRegex)

                        List buildParameters = []

                        String defaultMavenArgs
                        if (deployable) {
                            buildParameters.add(
                                    booleanParam(
                                            defaultValue: config.performRelease,
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
                                description: 'Maven arguments (does not include goals)',
                                name: 'mavenArguments'
                        ))

                        calculatedJobProperties = helper.calculateProperties(config.jobProperties, (Object) parameters(buildParameters))

                        // set job properties
                        //noinspection GroovyAssignabilityCheck
                        properties(calculatedJobProperties)

                        parsedConfiguration.performRelease = deployable && config.performRelease

                        if (wasTriggerByScm) {
                            // SCM change triggered build, use the parameter definitions from the configuration
                            parsedConfiguration.mavenArguments = parsedConfiguration.performRelease ? config.mavenDeployArguments : config.mavenNonDeployArguments
                        } else {
                            // manual build, use the values passed in the parameters
                            parsedConfiguration.mavenArguments = "${params.mavenArguments}"
                        }
                    }

                    // kill build if it goes longer than a given number of minutes without logging anything
                    //noinspection GroovyVariableNotAssigned
                    scope.withTimeout(config.timeoutMinutes) {

                        // TODO: dynamically generate cache location
                        String dockerArgs = """\
                            -v /var/run/docker.sock:/var/run/docker.sock 
                            -v jenkins-shared-m2-cache:'/root/.m2/repository'
                            """.stripIndent().replace("\n", "")

                        echo "Docker args: ${dockerArgs}"

                        // run build inside of docker build image
                        scope.inDockerImage(config.buildAgent, args: dockerArgs) {

                            build(parsedConfiguration, util)

                        }
                    }
                }
            }
        }
    }
}
