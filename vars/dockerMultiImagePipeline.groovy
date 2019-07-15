import zone.gryphon.pipeline.configuration.ConfigurationHelper
import zone.gryphon.pipeline.configuration.DockerMultiImagePipelineConfiguration
import zone.gryphon.pipeline.configuration.DockerMultiImagePipelineSingleImageConfiguration
import zone.gryphon.pipeline.configuration.effective.EffectiveDockerMultiImagePipelineConfiguration
import zone.gryphon.pipeline.configuration.effective.EffectiveDockerMultiImagePipelineSingleImageConfiguration
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

private List<String> build(EffectiveDockerMultiImagePipelineSingleImageConfiguration configuration) {

    stage("Build ${configuration.image}") {
        echo "hi"
        sleep 5
        echo "bye"
    }

    return []
}

private void push(EffectiveDockerMultiImagePipelineSingleImageConfiguration configuration, List<String> tags) {

    stage("Push ${configuration.image}") {
        echo "hi"
        sleep 5
        echo "bye"
    }
}

private static String directoryOf(String path) {
    return path.contains("/") ? path.substring(0, path.lastIndexOf('/')) : '.'
}

private EffectiveDockerMultiImagePipelineConfiguration parseConfiguration(String organization, Closure body) {
    final ConfigurationHelper helper = new ConfigurationHelper()

    final DockerMultiImagePipelineConfiguration config = helper.configure(organization, body, new DockerMultiImagePipelineConfiguration())

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

    out.images = config.images.collect { image ->
        DockerMultiImagePipelineSingleImageConfiguration imageConfig = helper.configure(image, new DockerMultiImagePipelineSingleImageConfiguration())

        EffectiveDockerMultiImagePipelineSingleImageConfiguration c = new EffectiveDockerMultiImagePipelineSingleImageConfiguration()

        String dockerOrg = imageConfig.dockerOrganization ?: defaultDockerOrganization
        String dockerArtifact = imageConfig.dockerArtifact


        c.image = "${dockerOrg}/${dockerArtifact}"


        c.baseVersion = imageConfig.version ?: '1.0'

        String buildContext = imageConfig.buildContext ?: directoryOf(imageConfig.dockerfile)

        // add global and specific build args
        c.buildArgs = String.join(' ', [
                globalBuildParams,
                imageConfig.buildArguments,
                "--file '${imageConfig.dockerfile}'",
                "'${buildContext}'"
        ].findAll {
            !(it == null || it.trim().isEmpty())
        })

        return c
    }

    out.buildAgent = config.buildAgent
    out.push = shouldPush
    out.timeoutMinutes = config.idleTimeout

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

    out.images.eachWithIndex { it, index ->
        String key = "Configuration for image #${index + 1} (${it.image})"

        String value = ''
        value += "  Image           : ${it.image}\n"
        value += "  Build arguments : ${it.buildArgs}\n"

        printableConfiguration[key] = value
    }

    helper.printConfiguration(printableConfiguration)

    return out
}

def call(String githubOrganization, Closure body) {
    final EffectiveDockerMultiImagePipelineConfiguration configuration
    final ScopeUtility scope = new ScopeUtility()

    // add standard pipeline wrappers.
    // this command also allocates a build agent for running the build.
    scope.withStandardPipelineWrappers {

        stage('Parse Configuration') {
            configuration = parseConfiguration(githubOrganization, body)
        }

        // kill build if it goes longer than a given number of minutes without logging anything
        scope.withTimeout(configuration.timeoutMinutes) {

            Map<Integer, List<String>> tags = [:]

            configuration.images.eachWithIndex { config, index ->
                tags[index] = build(config)
            }

            configuration.images.eachWithIndex { config, index ->
                push(config, tags[index])
            }
        }
    }
}

