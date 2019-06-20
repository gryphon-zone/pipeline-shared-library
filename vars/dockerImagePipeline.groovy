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

    // only instantiation of `Util` object should happen outside of timestamps block
    final Util util = new Util()

    util.withTimestamps {

        final DockerUtilities dockerUtilities = new DockerUtilities()
        final ConfigurationHelper helper = new ConfigurationHelper()

        CheckoutInformation checkoutInformation
        DockerPipelineConfiguration config
        List calculatedJobProperties
        List tags

        stage('Parse Configuration') {
            config = helper.configure(body, new DockerPipelineConfiguration())

            calculatedJobProperties = helper.calculateProperties(config.jobProperties)

            // set job properties
            //noinspection GroovyAssignabilityCheck
            properties(calculatedJobProperties)
        }

        stage('Await Executor') {
            node('docker-cli') {
                util.withRandomWorkspace {

                    stage('Checkout Project') {
                        checkoutInformation = util.checkoutProject()
                    }

                    JobInformation info = util.getJobInformation()

                    boolean deployable = info.branch.matches(config.deployableBranchRegex)

                    String dockerOrganization = config.dockerOrganization ?: dockerUtilities.convertToDockerHubName(info.organization)
                    String artifact = config.dockerArtifact ?: info.repository

                    String versionTagBase = config.version ? ("${config.version}.${info.build}-") : ""
                    String versionTag = "${versionTagBase}${checkoutInformation.gitCommit.substring(0, 7)}"

                    tags = [versionTag]

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
                }
            }
        }
    }
}
