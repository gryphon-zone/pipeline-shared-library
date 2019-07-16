import zone.gryphon.pipeline.configuration.ConfigurationHelper
import zone.gryphon.pipeline.configuration.DockerMultiImagePipelineConfiguration
import zone.gryphon.pipeline.configuration.DockerMultiImagePipelineSingleImageConfiguration
import zone.gryphon.pipeline.configuration.effective.EffectiveDockerMultiImagePipelineConfiguration
import zone.gryphon.pipeline.configuration.effective.EffectiveDockerMultiImagePipelineSingleImageConfiguration
import zone.gryphon.pipeline.model.CheckoutInformation
import zone.gryphon.pipeline.model.JobInformation
import zone.gryphon.pipeline.toolbox.DockerUtility
import zone.gryphon.pipeline.toolbox.ScopeUtility
import zone.gryphon.pipeline.toolbox.Util

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

private List<String> build(EffectiveDockerMultiImagePipelineSingleImageConfiguration configuration, List<String> defaultTags) {
    List<String> tags = []
    tags.addAll(defaultTags)
    tags.addAll(configuration.additionalTags)

    String buildImage = "${configuration.image}:${tags[0]}"

    dockerImage = docker.build(buildImage, configuration.buildArgs)


    echo "post build"

    return tags
}

private void push(EffectiveDockerMultiImagePipelineSingleImageConfiguration configuration, List<String> tags) {
    DockerUtility d = new DockerUtility()

    for (String tag : tags) {
        log.info("Pushing ${configuration.image}:${tag}")
    }
}

private static String directoryOf(String path) {
    return path.contains("/") ? path.substring(0, path.lastIndexOf('/')) : '.'
}


private static DockerMultiImagePipelineConfiguration validate(DockerMultiImagePipelineConfiguration config) {

    return config
}

private static DockerMultiImagePipelineSingleImageConfiguration validate(DockerMultiImagePipelineSingleImageConfiguration config, int index, String keyPrefix) {
    final String messagePrefix = "Configuration invalid: ${keyPrefix}[$index]"
    Objects.requireNonNull(config, messagePrefix + " may not be null")
    Objects.requireNonNull(config.dockerfile, messagePrefix + ".dockerfile may not be null")
    Objects.requireNonNull(config.artifact, messagePrefix + ".artifact may not be null")
    return config
}

private EffectiveDockerMultiImagePipelineConfiguration parseConfiguration(String organization, Closure body) {
    final ConfigurationHelper helper = new ConfigurationHelper()

    final DockerMultiImagePipelineConfiguration config = validate(helper.configure(organization, body, new DockerMultiImagePipelineConfiguration()))

    final EffectiveDockerMultiImagePipelineConfiguration out = new EffectiveDockerMultiImagePipelineConfiguration()

    final Util util = new Util()
    final DockerUtility dockerUtilities = new DockerUtility()

    final JobInformation info = util.getJobInformation()

    boolean deployable = helper.isDeployable(config, info)

    String defaultDockerOrganization = dockerUtilities.convertToDockerHubName(info.organization)

    List buildParameters = [
            string(
                    defaultValue: config.globalBuildArguments,
                    description: 'Arguments to pass to every invocation of "docker build"',
                    name: 'globalBuildArguments',
                    trim: true
            )
    ]

    if (deployable) {
        buildParameters.add(
                booleanParam(
                        defaultValue: config.push,
                        description: 'Whether or not to push the built Docker images',
                        name: 'push'
                )
        )
    }

    List calculatedJobProperties = helper.calculateProperties(config.jobProperties, (Object) parameters(buildParameters))

    // set job properties
    //noinspection GroovyAssignabilityCheck
    properties(calculatedJobProperties)

    boolean automatedRun = util.buildWasTriggerByCommit()

    String globalBuildParams = automatedRun ? config.globalBuildArguments : "${params.globalBuildArguments}"
    boolean shouldPush = deployable && (automatedRun ? config.push : "${params.push}".toBoolean())

    out.images = []
    out.buildAgent = config.buildAgent
    out.push = shouldPush
    out.timeoutMinutes = config.idleTimeout

    config.images.eachWithIndex { imageConfigurationClosure, index ->
        DockerMultiImagePipelineSingleImageConfiguration rawImageConfiguration = validate(helper.configure(imageConfigurationClosure, new DockerMultiImagePipelineSingleImageConfiguration()), index, 'images')

        EffectiveDockerMultiImagePipelineSingleImageConfiguration image = new EffectiveDockerMultiImagePipelineSingleImageConfiguration()

        String buildContext = rawImageConfiguration.buildContext ?: directoryOf(rawImageConfiguration.dockerfile)
        String dockerOrg = rawImageConfiguration.dockerOrganization ?: defaultDockerOrganization
        String dockerArtifact = rawImageConfiguration.artifact

        image.image = "${dockerOrg}/${dockerArtifact}"
        image.baseVersion = rawImageConfiguration.version ?: '1.0'
        image.additionalTags = shouldPush ? rawImageConfiguration.additionalTags : []

        // add global and specific build args
        image.buildArgs = String.join(' ', [
                globalBuildParams,
                rawImageConfiguration.buildArguments,
                "--file \"${rawImageConfiguration.dockerfile}\"",
                "'${buildContext}'"
        ].findAll {
            !(it == null || it.trim().isEmpty())
        })

        // add image to list of images to be built
        out.images.add(image)
    }


    Map printableConfiguration = [
            'Job is deployable'      : deployable,
            'Deployable organization': organization,
            'Deployable branches'    : config.deployableBranchRegex,
            'Push built images'      : shouldPush,
            'SCM organization'       : info.organization,
            'SCM repository'         : info.repository,
            'SCM branch'             : info.branch,
            'Job properties'         : helper.convertPropertiesToPrintableForm(calculatedJobProperties)
    ]

    out.images.eachWithIndex { image, index ->
        String key = "Configuration for image #${index + 1} (${image.image})"

        String value = ''
        value += "  Image                 : ${image.image}\n"
        value += "  Build arguments       : ${image.buildArgs}\n"
        value += "  Additional image tags : " + (image.additionalTags.isEmpty() ? '<none>' : String.join(', ', image.additionalTags))

        printableConfiguration[key] = value
    }

    helper.printConfiguration(printableConfiguration)

    return out
}

def call(String githubOrganization, Closure body) {
    final EffectiveDockerMultiImagePipelineConfiguration configuration
    final CheckoutInformation checkoutInformation

    final ScopeUtility scope = new ScopeUtility()
    final Util util = new Util()
    final List<String> defaultTags = []

    final JobInformation info = util.getJobInformation()

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

                stage('Checkout Project') {
                    // enable git color before performing checkout
                    util.enableGitColor()

                    checkoutInformation = util.checkoutProject()

                    String shortHash = Util.shortHash(checkoutInformation)

                    String branchTag = "${info.branch}-${shortHash}"

                    currentBuild.displayName = "${branchTag} (#${info.build})"
                    currentBuild.description = configuration.push ? "Release images" : "Build images"

                    defaultTags.add(configuration.push ? 'latest' : branchTag)
                }

                Map<Integer, List<String>> tags = [:]

                configuration.images.eachWithIndex { config, index ->
                    stage("Build ${config.image}") {
                        tags[index] = build(config, defaultTags)
                    }
                }

                configuration.images.eachWithIndex { config, index ->
                    stage("Push ${config.image}") {
                        push(config, tags[index])
                    }
                }

            }
        }
    }
}

