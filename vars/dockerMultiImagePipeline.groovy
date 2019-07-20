import zone.gryphon.pipeline.configuration.ConfigurationHelper
import zone.gryphon.pipeline.configuration.DockerMultiImagePipelineConfiguration
import zone.gryphon.pipeline.configuration.DockerMultiImagePipelineSingleImageConfiguration
import zone.gryphon.pipeline.configuration.effective.EffectiveDockerPipelineTemplateConfiguration
import zone.gryphon.pipeline.configuration.effective.EffectiveDockerPipelineTemplateSingleImageConfiguration
import zone.gryphon.pipeline.model.CheckoutInformation
import zone.gryphon.pipeline.model.JobInformation
import zone.gryphon.pipeline.template.DockerPipelineTemplate
import zone.gryphon.pipeline.toolbox.DockerUtility
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

private EffectiveDockerPipelineTemplateConfiguration parseConfiguration(
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

    final EffectiveDockerPipelineTemplateConfiguration out = new EffectiveDockerPipelineTemplateConfiguration()
    out.buildStageName = 'Build Images'
    out.pushStageName = 'Push Images'

    out.images = []
    out.push = (deployable && imagePushRequested)
    out.buildAgent = config.buildAgent
    out.timeoutMinutes = config.idleTimeout
    out.credentials = config.dockerCredentialsId

    config.images.eachWithIndex { imageConfigurationClosure, index ->
        DockerMultiImagePipelineSingleImageConfiguration rawImageConfiguration = validate(helper.configure(imageConfigurationClosure, new DockerMultiImagePipelineSingleImageConfiguration()), index, 'images')

        EffectiveDockerPipelineTemplateSingleImageConfiguration image = new EffectiveDockerPipelineTemplateSingleImageConfiguration()

        String buildContext = rawImageConfiguration.buildContext ?: Util.directoryOf(rawImageConfiguration.dockerfile)
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
    new DockerPipelineTemplate(this).call({
        CheckoutInformation scmInfo -> return parseConfiguration(githubOrganization, scmInfo, body)
    })
}

