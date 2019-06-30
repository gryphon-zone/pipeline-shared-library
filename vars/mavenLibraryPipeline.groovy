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

import java.util.regex.Pattern

private def performRelease(final ParsedMavenLibraryPipelineConfiguration config, final Util util, String mavenOpts) {

}

private def performBuild(final ParsedMavenLibraryPipelineConfiguration config, final Util util, String mavenOpts) {
    sh "MAVEN_OPTS=\"${mavenOpts}\" mvn clean verify ${config.mavenArguments}"
}

private def build(final ParsedMavenLibraryPipelineConfiguration config, final Util util) {
    CheckoutInformation checkoutInformation

    stage('Checkout Project') {
        checkoutInformation = util.checkoutProject()
    }

    String mavenOpts = (util.sh('echo $MAVEN_OPTS', quiet: true) + ' -Djansi.force=true').trim()

    if (config.performRelease) {
        performRelease(config, util, mavenOpts)
    } else {
        performBuild(config, util, mavenOpts)
    }
}

def call(String githubOrganization, Closure body) {

    // only call outside of timestamp block is creation of ScopeUtility,
    // all other calls in the pipeline should happen inside of the timestamp block
    final ScopeUtility scope = new ScopeUtility()

    scope.withTimestamps {

        // no build is allowed to run for more than 1 hour
        scope.withAbsoluteTimeout(60) {

            // run all commands inside docker agent
            scope.withExecutor('docker') {

                // add support for ANSI color
                scope.withColor {

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

                        if (deployable) {
                            buildParameters.add(
                                    booleanParam(
                                            defaultValue: config.performRelease,
                                            description: 'Whether or not to release the maven artifacts',
                                            name: 'performRelease'
                                    )
                            )
                        }

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
                            parsedConfiguration.mavenArguments = parsedConfiguration.performRelease ? config.mavenDeployArguments : config.mavenNonDeployArguments
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
