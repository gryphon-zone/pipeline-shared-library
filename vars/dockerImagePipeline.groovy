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
import zone.gryphon.pipeline.configuration.DockerPipelineConfiguration
import zone.gryphon.pipeline.model.CheckoutInformation
import zone.gryphon.pipeline.model.JobInformation
import zone.gryphon.pipeline.toolbox.DockerUtility
import zone.gryphon.pipeline.toolbox.ScopeUtility
import zone.gryphon.pipeline.toolbox.Util

import java.util.regex.Pattern

def call(String githubOrganization, Closure body) {

    // only call outside of timestamp block is creation of ScopeUtility,
    // all other calls in the pipeline should happen inside of the timestamp block
    final ScopeUtility scope = new ScopeUtility()

    scope.withTimestamps {

        scope.withColor {

            // no build is allowed to run for more than 60 minutes
            scope.withAbsoluteTimeout(60) {

                final Util util = new Util()
                final DockerUtility dockerUtilities = new DockerUtility()
                final ConfigurationHelper helper = new ConfigurationHelper()
                final JobInformation info = util.getJobInformation()
                boolean wasTriggerByScm = util.buildWasTriggerByCommit()

                CheckoutInformation checkoutInformation
                DockerPipelineConfiguration config
                List calculatedJobProperties
                String dockerOrganization
                String artifact
                boolean deployable

                String buildArgs
                String buildContext
                boolean pushImage

                def dockerImage
                String dockerImageId

                stage('Parse Configuration') {
                    config = helper.configure(body, new DockerPipelineConfiguration())

                    // branch is deployable if it matches the regex AND it's not from a fork
                    deployable = (githubOrganization == info.organization) && info.branch.matches(config.deployableBranchRegex)
                    dockerOrganization = config.dockerOrganization ?: dockerUtilities.convertToDockerHubName(info.organization)
                    artifact = config.dockerArtifact ?: info.repository


                    List buildParameters = [
                            string(
                                    defaultValue: config.buildArgs,
                                    description: 'Arguments to pass to the "docker build" command',
                                    name: 'buildArgs',
                                    trim: true
                            ),
                            string(
                                    defaultValue: config.buildContext,
                                    description: 'Build context to use for the "docker build" command',
                                    name: 'buildContext',
                                    trim: true
                            )
                    ]

                    if (deployable) {
                        buildParameters.add(
                                booleanParam(
                                        defaultValue: true,
                                        description: 'Whether or not to push the built Docker image',
                                        name: 'pushImage'
                                )
                        )
                    }

                    calculatedJobProperties = helper.calculateProperties(config.jobProperties, (Object) parameters(buildParameters))

                    // set job properties
                    //noinspection GroovyAssignabilityCheck
                    properties(calculatedJobProperties)

                    if (wasTriggerByScm) {
                        // SCM change triggered build, use the parameter definitions from the configuration
                        buildArgs = config.buildArgs
                        buildContext = config.buildContext
                        pushImage = config.pushImage
                    } else {
                        // manual build, use the values passed in the parameters
                        buildArgs = "${params.buildArgs}"
                        buildContext = "${params.buildContext}"
                        pushImage = "${params.pushImage}".toBoolean()
                    }
                }

                // kill build if it goes longer than a given number of minutes without logging anything
                //noinspection GroovyVariableNotAssigned
                scope.withTimeout(config.timeoutMinutes) {
                    scope.withExecutor(config.nodeType) {

                        stage('Checkout Project') {
                            checkoutInformation = util.checkoutProject()
                        }

                        String shortHash = Util.shortHash(checkoutInformation)

                        String branchTag = "${info.branch}-${shortHash}"

                        List tags = []

                        if (deployable) {
                            // if there's a version defined in the config, generate a "version.build-hash"
                            // tag for the image. This will typically look like 1.2.3-abcdef;
                            // otherwise, use the branch tag.
                            // This is to ensure we always have a unique tag for each image, since the
                            // "latest" tag will be overwritten by subsequent builds.
                            if (config.version) {
                                tags.add("${config.version}-${shortHash}")
                            } else {
                                tags.add(branchTag)
                            }

                            tags.add("latest")
                        } else {
                            // non-deployable branches always get tagged with the branch name,
                            // so it's obvious where they came from
                            tags.add(branchTag)
                        }


                        currentBuild.displayName = "${dockerUtilities.coordinatesFor(dockerOrganization, artifact, "${tags[0]}")} (#${info.build})"
                        currentBuild.description = "Image tagged with ${String.join(', ', tags)}"

                        String propertiesToString = String.join("\n", calculatedJobProperties.collect { prop -> "\t${prop}".replace('<anonymous>=', '') })

                        echo """\
                        ${'#' * 120}
                        ${'#' * 120}
                        Calculated Configuration:
                        -------------------------
                        Github Organization: ${githubOrganization}
                        Dockerhub Organization: ${dockerOrganization}
                        Dockerhub Repository: ${artifact}
                        Docker Image Tags: ${tags}
                        Docker build arguments: ${buildArgs}
                        Docker build context: ${buildContext}
                        """.stripIndent()
                                .concat("Job Properties:\n${propertiesToString}\n")
                                .concat('#' * 120).concat('\n')
                                .concat('#' * 120).concat('\n')

                        String buildTag = dockerUtilities.coordinatesFor(dockerOrganization, artifact, Util.entropy())

                        stage('Build Docker Image') {
                            dockerImage = docker.build(buildTag, "${buildArgs} ${buildContext}")
                            dockerImageId = util.sh("docker images ${buildTag} --format '{{.ID}}' | head -n 1", quiet: true).trim()
                        }

                        stage('Tag docker image') {

                            tags.each { tag ->
                                dockerImage.tag(tag)
                            }

                        }

                        stage('Print Docker Image Information') {
                            String strings = String.join('|', tags.collect { tag -> Pattern.quote("${tag}") })
                            String imageData = util.sh("docker images '${dockerOrganization}/${artifact}' | grep -E 'REPOSITORY|${dockerImageId}' | grep -P '(^REPOSITORY\\s+|${strings})'", quiet: true).trim()
                            echo "Built the following images:\n${imageData}"
                        }


                        if (deployable) {
                            stage('Push Docker image') {
                                if (pushImage) {
                                    withCredentials([usernamePassword(credentialsId: config.dockerCredentialsId, passwordVariable: 'password', usernameVariable: 'username')]) {
                                        try {
                                            util.sh("echo \"${password}\" | docker login -u \"${username}\" --password-stdin", quiet: true)

                                            tags.each { tag ->
                                                dockerImage.push(tag)
                                            }

                                        } finally {
                                            util.sh("docker logout", quiet: true)
                                        }
                                    }
                                } else {
                                    echo 'Not pushing image, disabled via parameter'
                                }
                            }
                        } else {
                            echo "Not pushing image, branch \"${info.organization}/${info.repository}/${info.branch}\" is not deployable"
                        }

                        util.sh("docker rmi ${buildTag}", quiet: true)
                    }
                }
            }
        }
    }
}
