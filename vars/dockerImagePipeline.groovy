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
import zone.gryphon.pipeline.configuration.effective.EffectiveDockerPipelineTemplateConfiguration
import zone.gryphon.pipeline.configuration.effective.EffectiveDockerPipelineTemplateSingleImageConfiguration
import zone.gryphon.pipeline.model.CheckoutInformation
import zone.gryphon.pipeline.model.JobInformation
import zone.gryphon.pipeline.template.DockerPipelineTemplate
import zone.gryphon.pipeline.toolbox.DockerUtility
import zone.gryphon.pipeline.toolbox.ScopeUtility
import zone.gryphon.pipeline.toolbox.Util

private static DockerPipelineConfiguration validate(DockerPipelineConfiguration config) {
    Objects.requireNonNull(config, 'Configuration may not be null')
    Objects.requireNonNull(config.dockerCredentialsId, "Credentials ID may not be null")
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
    final DockerPipelineConfiguration config = validate(helper.configure(organization, body, new DockerPipelineConfiguration()))
    final JobInformation info = util.getJobInformation()
    final String defaultDockerOrganization = dockerUtilities.convertToDockerHubName(info.organization)
    final String buildContext = config.buildContext ?: Util.directoryOf(config.dockerfile)
    final String branchTag = "${info.branch}-${Util.shortHash(checkoutInformation)}"
    final boolean deployable = helper.isDeployable(config, info)
    final boolean automatedRun = util.buildWasTriggerByCommit()

    // configuration
    final List buildParameters = []

    String dockerOrganization = config.dockerOrganization ?: defaultDockerOrganization
    String artifact = config.dockerArtifact ?: info.repository

    buildParameters.add(
            string(
                    defaultValue: config.buildArgs,
                    description: 'Arguments to pass to the "docker build" command',
                    name: 'buildArgs',
                    trim: true
            )
    )

    if (deployable) {
        buildParameters.add(
                booleanParam(
                        defaultValue: true,
                        description: 'Whether or not to push the built Docker image',
                        name: 'push'
                )
        )
    }

    List calculatedJobProperties = helper.calculateAndAssignJobProperties(config, (Object) parameters(buildParameters))

    final EffectiveDockerPipelineTemplateConfiguration out = new EffectiveDockerPipelineTemplateConfiguration()
    final EffectiveDockerPipelineTemplateSingleImageConfiguration image = new EffectiveDockerPipelineTemplateSingleImageConfiguration()

    final boolean deployRequested = "${(automatedRun ? config.pushImage : params.push)}".toBoolean()
    final String buildArgs = "${automatedRun ? config.buildArgs : params.buildArgs}"

    out.buildStageName = 'Build Image'
    out.pushStageName = 'Push Image'

    out.images = [image]
    out.push = deployable && deployRequested

    out.credentials = config.dockerCredentialsId
    out.buildAgent = config.buildAgent
    out.timeoutMinutes = config.idleTimeout

    image.tags = [branchTag]
    image.image = "${dockerOrganization}/${artifact}"
    image.buildArgs = String.join(" ", Util.nonEmpty([
            buildArgs,
            "--file '${config.dockerfile}'",
            "'${buildContext}'"
    ]))

    if (out.push) {

        if (config.tags) {
            image.tags.addAll(config.tags)
        }

        if (config.tagAsLatest) {
            image.tags.add('latest')
        }
    }

    helper.printConfiguration([
            'Job is deployable'      : deployable,
            'Deployable organization': organization,
            'Deployable branches'    : config.deployableBranchRegex,
            'Push built images'      : out.push,
            'SCM organization'       : info.organization,
            'SCM repository'         : info.repository,
            'SCM branch'             : info.branch,
            'Docker image'           : image.image,
            'Docker build arguments' : image.buildArgs,
            'Docker image tags'      : String.join(', ', image.tags),
            'Job properties'         : helper.convertPropertiesToPrintableForm(calculatedJobProperties)
    ])

    return out
}

def call(String githubOrganization, Closure body) {
    final EffectiveDockerPipelineTemplateConfiguration configuration
    final CheckoutInformation checkoutInformation

    final ScopeUtility scope = new ScopeUtility()
    final Util util = new Util()

    // add standard pipeline wrappers.
    // this command also allocates a build agent for running the build.
    scope.withStandardPipelineWrappers {

        stage('Checkout Project') {
            log.info('Checking out project...')

            checkoutInformation = util.checkoutProject()
        }

        stage('Parse Configuration') {
            log.info('Parsing build configuration...')

            configuration = parseConfiguration(githubOrganization, checkoutInformation, body)
        }

        new DockerPipelineTemplate(this).call(configuration)
    }
}

