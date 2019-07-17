import zone.gryphon.pipeline.configuration.ConfigurationHelper
import zone.gryphon.pipeline.configuration.DockerMultiImagePipelineConfiguration
import zone.gryphon.pipeline.configuration.DockerMultiImagePipelineSingleImageConfiguration
import zone.gryphon.pipeline.configuration.effective.EffectiveDockerMultiImagePipelineConfiguration
import zone.gryphon.pipeline.configuration.effective.EffectiveDockerMultiImagePipelineSingleImageConfiguration
import zone.gryphon.pipeline.model.CheckoutInformation
import zone.gryphon.pipeline.model.JobInformation
import zone.gryphon.pipeline.toolbox.DockerUtility
import zone.gryphon.pipeline.toolbox.ScopeUtility
import zone.gryphon.pipeline.toolbox.TextColor
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

private void build(EffectiveDockerMultiImagePipelineSingleImageConfiguration configuration) {
    final TextColor c = TextColor.instance
    final Util util = new Util()

    String buildTags = String.join(' ', configuration.tags
            .collect { "${configuration.image}:${it}" }
            .collect { "--tag '${it}'" })

    // can't use the "docker" global variable to build the image because it will always throw an
    // an exception attempting to fingerprint the Dockerfile if the path to the Dockerfile contains any spaces.
    //
    // Since not supporting spaces in paths is ridiculous in the year of our lord 2019,
    // there's no way to turn this fingerprinting off,
    // and it provides little value,
    // just invoke docker build ourselves.
    log.info("Building \"${c.bold(configuration.image)}\"...")
    long start = System.currentTimeMillis()
    util.sh("docker build ${buildTags} ${configuration.buildArgs}", returnType: 'none')
    long duration = System.currentTimeMillis() - start
    log.info("Built \"${c.bold(configuration.image)}\" in ${duration / 1000} seconds")
}

private void push(EffectiveDockerMultiImagePipelineSingleImageConfiguration configuration) {
    final Util util = new Util()

    for (String tag : configuration.tags) {
        String name = "${configuration.image}:${tag}"
        log.info("Pushing ${name}...")
        long start = System.currentTimeMillis()
        util.sh("docker push '${name}'", returnType: 'none')
        long duration = System.currentTimeMillis() - start
        log.info("Pushed ${name} in ${duration / 1000} seconds")
    }
}

private static String directoryOf(String path) {
    return path.contains("/") ? path.substring(0, path.lastIndexOf('/')) : '.'
}


private static DockerMultiImagePipelineConfiguration validate(DockerMultiImagePipelineConfiguration config) {
    Objects.requireNonNull(config, 'Configuration may not be null')
    Objects.requireNonNull(config.dockerCredentialsId, "Credentials ID may not be null")
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
    out.push = shouldPush
    out.buildAgent = config.buildAgent
    out.timeoutMinutes = config.idleTimeout
    out.credentials = config.dockerCredentialsId

    config.images.eachWithIndex { imageConfigurationClosure, index ->
        DockerMultiImagePipelineSingleImageConfiguration rawImageConfiguration = validate(helper.configure(imageConfigurationClosure, new DockerMultiImagePipelineSingleImageConfiguration()), index, 'images')

        EffectiveDockerMultiImagePipelineSingleImageConfiguration image = new EffectiveDockerMultiImagePipelineSingleImageConfiguration()

        String buildContext = rawImageConfiguration.buildContext ?: directoryOf(rawImageConfiguration.dockerfile)
        String dockerOrg = rawImageConfiguration.dockerOrganization ?: defaultDockerOrganization
        String dockerArtifact = rawImageConfiguration.artifact

        image.image = "${dockerOrg}/${dockerArtifact}"
        image.tags = []

        if (shouldPush) {

            if (rawImageConfiguration.tagAsLatest) {
                image.tags.add('latest')
            }

            image.tags.addAll(rawImageConfiguration.additionalTags)
        }

        // add global and specific build args
        image.buildArgs = String.join(' ', [
                globalBuildParams,
                rawImageConfiguration.buildArguments,
                "--file '${rawImageConfiguration.dockerfile}'",
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
        value += "  Image           : ${image.image}\n"
        value += "  Build arguments : ${image.buildArgs}\n"
        value += "  Image tags      : " + (image.tags.isEmpty() ? '<none>' : String.join(', ', image.tags))

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

                    // now that we know it, add the branch tag to the list of image tags
                    configuration.images.each {it.tags.add(branchTag)}
                }

                stage('Build Docker Images') {
                    configuration.images.eachWithIndex { config, index ->
                        build(config)
                    }
                }

                if (configuration.push) {
                    stage('Push Docker Images') {
                        log.info("Using credentials \"${configuration.credentials}\" for pushing Docker images")
                        scope.withDockerAuthentication(configuration.credentials) {
                            configuration.images.eachWithIndex { config, index ->
                                push(config)
                            }
                        }
                    }
                }
            }
        }
    }
}

