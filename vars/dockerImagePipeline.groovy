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
import zone.gryphon.pipeline.toolbox.DockerUtilities
import zone.gryphon.pipeline.toolbox.Util

def call(String githubOrganization, Closure body) {

    // only call outside of timestamp block is creation of util object
    final Util util = new Util()

    util.withTimestamps {

        final DockerUtilities dockerUtilities = new DockerUtilities()
        final ConfigurationHelper helper = new ConfigurationHelper()

        CheckoutInformation checkoutInformation
        DockerPipelineConfiguration config
        List calculatedJobProperties
        def image

        // no build is allowed to run for more than 60 minutes
        util.withAbsoluteTimeout(60) {

            util.withColor {

                stage('Parse Configuration') {
                    config = helper.configure(body, new DockerPipelineConfiguration())

                    calculatedJobProperties = helper.calculateProperties(config.jobProperties)

                    // set job properties
                    //noinspection GroovyAssignabilityCheck
                    properties(calculatedJobProperties)
                }

                // kill build if it goes longer than a given number of minutes without logging anything
                util.withTimeout(config.timeoutMinutes) {
                    stage('Await Executor') {
                        node(config.nodeType) {
                            util.withRandomWorkspace {
                                try {

                                    stage('Checkout Project') {
                                        checkoutInformation = util.checkoutProject()
                                    }

                                    JobInformation info = util.getJobInformation()

                                    boolean deployable = info.branch.matches(config.deployableBranchRegex)

                                    String dockerOrganization = config.dockerOrganization ?: dockerUtilities.convertToDockerHubName(info.organization)
                                    String artifact = config.dockerArtifact ?: info.repository

                                    String shortHash = checkoutInformation.gitCommit.substring(0, 7)
                                    String versionTagBase = config.version ? ("${config.version}.${info.build}-") : ""
                                    String versionTag = "${versionTagBase}${shortHash}"
                                    String branchTag = "${info.branch}-${shortHash}"

                                    List tags = [branchTag, versionTag]

                                    if (deployable) {
                                        tags.add('latest')
                                    }

                                    echo """\
                                    Github Organization: ${githubOrganization}
                                    Docker Organization: ${dockerOrganization}
                                    Docker Artifact: ${artifact}
                                    Docker Tags: ${tags}
                                    Properties: ${calculatedJobProperties}
                                    """.stripIndent()

                                    String initialTag = Util.entropy()

                                    stage ('Build Docker Image') {
                                        image = docker.build(dockerUtilities.coordinatesFor(dockerOrganization, artifact, initialTag), "--pull --progress 'plain' .")
                                    }

                                    stage('Tag docker image') {
                                        tags.each { tag ->
                                            image.tag(tag)
                                        }
                                    }

                                    if (deployable) {
                                        stage('Push Docker image') {
                                            withCredentials([usernamePassword(credentialsId: config.dockerCredentialsId, passwordVariable: 'password', usernameVariable: 'username')]) {
                                                sh """
                                                   { set +x; } 2> /dev/null && \
                                                   echo \"${password}\" | docker login -u \"${username}\" --password-stdin
                                                   """

                                                tags.each { tag ->
                                                    image.push(tag)
                                                }
                                            }
                                        }
                                    }
                                } finally {
                                    cleanWs(notFailBuild: true)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
