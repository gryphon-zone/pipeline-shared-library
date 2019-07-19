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

@SuppressWarnings("GrMethodMayBeStatic")
private void build(EffectiveDockerMultiImagePipelineSingleImageConfiguration configuration) {
    final TextColor c = TextColor.instance
    final Util util = new Util()
    final DockerUtility dockerUtility = new DockerUtility()

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

    String imageInfo = dockerUtility.dockerImagesInfoForGivenTags(configuration.image, configuration.tags)

    log.info("Successfully built the following images for \"${c.bold(configuration.image)}\" in ${duration / 1000} seconds:\n${imageInfo}")
}

@SuppressWarnings("GrMethodMayBeStatic")
private void push(EffectiveDockerMultiImagePipelineSingleImageConfiguration configuration) {
    final TextColor c = TextColor.instance
    final Util util = new Util()

    for (String tag : configuration.tags) {
        String name = "${configuration.image}:${tag}"
        log.info("Pushing \"${c.bold(name)}\"...")
        long start = System.currentTimeMillis()
        util.sh("docker push '${name}'", returnType: 'none')
        long duration = System.currentTimeMillis() - start
        log.info("Pushed  \"${c.bold(name)}\" in ${duration / 1000} seconds")
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

private EffectiveDockerMultiImagePipelineConfiguration parseConfiguration(
        String organization,
        CheckoutInformation checkoutInformation,
        Closure body) {

    // utilities
    final ConfigurationHelper helper = new ConfigurationHelper()
    final Util util = new Util()
    final DockerUtility dockerUtilities = new DockerUtility()

    // info
    final DockerMultiImagePipelineConfiguration config = validate(helper.configure(organization, body, new DockerMultiImagePipelineConfiguration()))
    final JobInformation info = util.getJobInformation()
    final String defaultDockerOrganization = dockerUtilities.convertToDockerHubName(info.organization)
    final String branchTag = "${info.branch}-${Util.shortHash(checkoutInformation)}"
    final boolean deployable = helper.isDeployable(config, info)
    final boolean automatedRun = util.buildWasTriggerByCommit()

    // configuration
    final List buildParameters = []

    buildParameters.add(
            string(
                    defaultValue: config.globalBuildArguments,
                    description: 'Arguments to pass to every invocation of "docker build"',
                    name: 'globalBuildArguments',
                    trim: true
            )
    )

    if (deployable) {
        buildParameters.add(
                booleanParam(
                        defaultValue: config.push,
                        description: 'Whether or not to push the built Docker images',
                        name: 'push'
                )
        )
    }

    final List calculatedJobProperties = helper.calculateAndAssignJobProperties(config, (Object) parameters(buildParameters))

    // parse params
    final String globalBuildParams = automatedRun ? config.globalBuildArguments : "${params.globalBuildArguments}"
    final boolean imagePushRequested = automatedRun ? config.push : "${params.push}".toBoolean()

    final EffectiveDockerMultiImagePipelineConfiguration out = new EffectiveDockerMultiImagePipelineConfiguration()

    out.images = []
    out.push = (deployable && imagePushRequested)
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
        image.tags = [branchTag]

        if (out.push) {

            image.tags.addAll(rawImageConfiguration.additionalTags)

            if (rawImageConfiguration.tagAsLatest) {
                image.tags.add('latest')
            }
        }

        // add global and specific build args
        image.buildArgs = String.join(' ', Util.nonEmpty([
                globalBuildParams,
                rawImageConfiguration.buildArguments,
                "--file '${rawImageConfiguration.dockerfile}'",
                "'${buildContext}'"
        ]))

        // add image to list of images to be built
        out.images.add(image)
    }

    // set build information
    currentBuild.displayName = "${branchTag} (#${info.build})"
    currentBuild.description = out.push ? "Release images" : "Build images"

    final Map printableConfiguration = [
            'Job is deployable'      : deployable,
            'Deployable organization': organization,
            'Deployable branches'    : config.deployableBranchRegex,
            'Push built images'      : out.push,
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
        value += "  Image tags      : ${String.join(', ', image.tags)}"

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

    // add standard pipeline wrappers, and allocate default build executor (node)
    scope.withStandardPipelineWrappers {

        stage('Checkout Project') {
            log.info('Checking out project')

            checkoutInformation = util.checkoutProject()
        }

        stage('Parse Configuration') {
            log.info('Parsing build configuration')

            configuration = parseConfiguration(githubOrganization, checkoutInformation, body)
        }

        // kill build if it goes longer than a given number of minutes without logging anything
        scope.withTimeout(configuration.timeoutMinutes) {

            // run build inside of docker build image
            scope.inDockerImage(configuration.buildAgent) {

                stage('Build Docker Images') {
                    configuration.images.each { config ->
                        build(config)
                    }
                }

                if (configuration.push) {
                    stage('Push Docker Images') {
                        log.info("Using credentials \"${configuration.credentials}\" for pushing Docker images")
                        scope.withDockerAuthentication(configuration.credentials) {
                            configuration.images.each { config ->
                                push(config)
                            }
                        }
                    }
                }
            }
        }
    }
}

