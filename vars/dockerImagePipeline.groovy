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
import zone.gryphon.pipeline.configuration.effective.EffectiveDockerImagePipelineConfiguration
import zone.gryphon.pipeline.model.CheckoutInformation
import zone.gryphon.pipeline.model.JobInformation
import zone.gryphon.pipeline.toolbox.DockerUtility
import zone.gryphon.pipeline.toolbox.ScopeUtility
import zone.gryphon.pipeline.toolbox.Util

import java.util.regex.Pattern

private EffectiveDockerImagePipelineConfiguration parseConfiguration(String githubOrganization, Closure body) {
    final ConfigurationHelper helper = new ConfigurationHelper()

    final DockerPipelineConfiguration config = helper.configure(body, new DockerPipelineConfiguration())
    final EffectiveDockerImagePipelineConfiguration out = new EffectiveDockerImagePipelineConfiguration()

    final Util util = new Util()
    final DockerUtility dockerUtilities = new DockerUtility()

    final JobInformation info = util.getJobInformation()

    // branch is deployable if it matches the regex AND it's not from a fork
    boolean deployable = (githubOrganization == info.organization) && info.branch.matches(config.deployableBranchRegex)
    String dockerOrganization = config.dockerOrganization ?: dockerUtilities.convertToDockerHubName(info.organization)
    String artifact = config.dockerArtifact ?: info.repository

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
                        name: 'push'
                )
        )
    }

    List calculatedJobProperties = helper.calculateProperties(config.jobProperties, (Object) parameters(buildParameters))

    // set job properties
    //noinspection GroovyAssignabilityCheck
    properties(calculatedJobProperties)

    if (util.buildWasTriggerByCommit()) {
        // SCM change triggered build, use the parameter definitions from the configuration
        out.buildArgs = config.buildArgs
        out.buildContext = config.buildContext
        out.push = deployable && config.pushImage
    } else {
        // manual build, use the values passed in the parameters
        out.buildArgs = "${params.buildArgs}"
        out.buildContext = "${params.buildContext}"
        out.push = deployable && "${params.push}".toBoolean()
    }

    out.image = "${dockerOrganization}/${artifact}"
    out.buildAgent = config.buildAgent
    out.baseVersion = config.version
    out.timeoutMinutes = config.timeoutMinutes

    // credentials may be configurable in the future
    out.credentials = 'docker'

    helper.printConfiguration([
            'Job is deployable'      : deployable,
            'Deployable organization': githubOrganization,
            'Deployable branches'    : config.deployableBranchRegex,
            'SCM organization'       : info.organization,
            'SCM repository'         : info.repository,
            'SCM branch'             : info.branch,
            'Build agent'            : out.buildAgent,
            'Docker image'           : out.image,
            'Docker build arguments' : out.buildArgs,
            'Docker build context'   : out.buildContext,
            'Push built image'       : out.push,
            'Job properties'         : helper.convertPropertiesToPrintableForm(calculatedJobProperties)
    ])

    return out
}

private void build(final EffectiveDockerImagePipelineConfiguration configuration) {
    final CheckoutInformation checkoutInformation
    final Util util = new Util()
    final DockerUtility dockerUtilities = new DockerUtility()
    final JobInformation info = util.getJobInformation()
    final ScopeUtility scope = new ScopeUtility()
    final List<String> tags = []
    final String dockerImageName
    def dockerImage

    stage('Checkout Project') {
        // enable git color before performing checkout
        util.enableGitColor()

        checkoutInformation = util.checkoutProject()
    }

    stage('Configure Project') {
        String shortHash = Util.shortHash(checkoutInformation)

        String branchTag = "${info.branch}-${shortHash}"

        if (configuration.push) {
            // if there's a version defined in the config, generate a "version.build-hash"
            // tag for the image. This will typically look like 1.2.3-abcdef;
            // otherwise, use the branch tag.
            // This is to ensure we always have a unique tag for each image, since the
            // "latest" tag will be overwritten by subsequent builds.
            if (configuration.baseVersion) {
                tags.add("${configuration.baseVersion}-${shortHash}")
            } else {
                tags.add(branchTag)
            }

            tags.add("latest")
        } else {
            // non-deployable branches always get tagged with the branch name,
            // so it's obvious where they came from
            tags.add(branchTag)
        }

        dockerImageName = dockerUtilities.tag(configuration.image, tags[0])

        currentBuild.displayName = "${dockerImageName} (#${info.build})"
        currentBuild.description = "Image tagged with ${String.join(', ', tags)}"
    }

    stage('Docker Image Build') {

        // build and tag image
        dockerImage = docker.build(dockerImageName, "${configuration.buildArgs} ${configuration.buildContext}")
        tags.each { tag -> dockerImage.tag(tag) }

        // get the unique image ID
        String dockerImageId = util.sh("docker images ${dockerImageName} --format '{{.ID}}' | head -n 1", quiet: true).trim()

        // log the docker image data for all built tags
        String patterns = String.join('|', tags.collect { tag -> Pattern.quote("${tag}") })
        String dockerImageMetadata = util.sh("""\
            docker images '${configuration.image}' |\
            grep -E 'REPOSITORY|${dockerImageId}' |\
            grep -P '(^REPOSITORY\\s+|${patterns})'\
            """, quiet: true).trim()

    }

    if (configuration.push) {
        stage('Docker image Push') {
            scope.withDockerAuthentication(configuration.credentials) {
                tags.each { tag -> dockerImage.push(tag) }
            }
        }
    }
}

def call(String githubOrganization, Closure body) {
    final EffectiveDockerImagePipelineConfiguration configuration
    final ScopeUtility scope = new ScopeUtility()

    // add standard pipeline wrappers.
    // this command also allocates a build agent for running the build.
    scope.withStandardPipelineWrappers {

        stage('Parse Configuration') {
            configuration = parseConfiguration(githubOrganization, body)
        }

        // kill build if it goes longer than a given number of minutes without logging anything
        scope.withTimeout(configuration.timeoutMinutes) {

            // run build inside of docker build image
            scope.inDockerImage(configuration.buildAgent) {

                build(configuration)

            }
        }
    }
}

