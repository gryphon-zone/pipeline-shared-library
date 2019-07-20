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

package zone.gryphon.pipeline.template


import zone.gryphon.pipeline.configuration.effective.EffectiveDockerPipelineTemplateConfiguration
import zone.gryphon.pipeline.configuration.effective.EffectiveDockerPipelineTemplateSingleImageConfiguration
import zone.gryphon.pipeline.model.CheckoutInformation
import zone.gryphon.pipeline.toolbox.DockerUtility
import zone.gryphon.pipeline.toolbox.ScopeUtility
import zone.gryphon.pipeline.toolbox.TextColor
import zone.gryphon.pipeline.toolbox.Util

class DockerPipelineTemplate {

    private final TextColor text = TextColor.instance

    private final ScopeUtility scope = new ScopeUtility()

    private final Util util = new Util()

    private final DockerUtility dockerUtility = new DockerUtility()

    private final def context

    /**
     *
     * @param context Jenkins script context, available using the keyword "{@code this}" to code running in a Jenkinsfile
     */
    DockerPipelineTemplate(Object context) {
        this.context = Objects.requireNonNull(context, "Script context must be provided")
    }

    def call(Closure<EffectiveDockerPipelineTemplateConfiguration> configurationClosure) {
        Objects.requireNonNull(configurationClosure, "Configuration closure must be provided")
        configurationClosure.resolveStrategy = Closure.OWNER_FIRST
        configurationClosure.delegate = this

        final EffectiveDockerPipelineTemplateConfiguration configuration
        final CheckoutInformation checkoutInformation

        // add standard pipeline wrappers, and allocate default build executor (node)
        scope.withStandardPipelineWrappers {

            context.stage('Checkout Project') {
                context.log.info('Checking out project...')

                checkoutInformation = util.checkoutProject()
            }

            context.stage('Parse Configuration') {
                context.log.info('Parsing build configuration...')

                configuration = configurationClosure.call(checkoutInformation)
            }

            context.log.info("Configuring build idle timeout...")
            scope.withTimeout(configuration.timeoutMinutes) {

                context.log.info("Running build inside of \"${configuration.buildAgent}\"...")
                scope.inDockerImage(configuration.buildAgent) {

                    context.stage(configuration.buildStageName) {
                        configuration.images.eachWithIndex { image, index ->
                            context.log.info("Building \"${text.bold(image.image)}\" (image ${index + 1} of ${configuration.images.size()})...")
                            build(image)
                        }
                    }

                    if (configuration.push) {
                        context.stage(configuration.pushStageName) {

                            context.log.info("Using credentials \"${configuration.credentials}\" for pushing Docker images")

                            scope.withDockerAuthentication(configuration.credentials) {
                                configuration.images.eachWithIndex { image, index ->
                                    context.log.info("Pushing \"${text.bold(image.image)}\" (image ${index + 1} of ${configuration.images.size()})...")
                                    push(image)
                                }
                            }
                        }
                    }
                }
            }

        }
    }

    @SuppressWarnings("GrMethodMayBeStatic")
    private void build(EffectiveDockerPipelineTemplateSingleImageConfiguration configuration) {

        String imageTagArguments = String.join(' ', configuration.tags
                .collect { tag -> dockerUtility.tag(configuration.image, tag) }
                .collect { tag -> "--tag '${tag}'" })

        // can't use the "docker" global variable to build the image because it will always throw an
        // an exception attempting to fingerprint the Dockerfile if the path to the Dockerfile contains any spaces.
        //
        // Since not supporting spaces in paths is ridiculous in the year of our lord 2019,
        // there's no way to turn this fingerprinting off,
        // and it provides little value,
        // just invoke docker build ourselves.
        long start = System.currentTimeMillis()
        util.sh("docker build ${imageTagArguments} ${configuration.buildArgs}", returnType: 'none')
        long duration = System.currentTimeMillis() - start

        String imageInfo = dockerUtility.dockerImagesInfoForGivenTags(configuration.image, configuration.tags)

        context.log.info("Successfully built the following images for \"${text.bold(configuration.image)}\" in ${duration / 1000} seconds:\n${imageInfo}")
    }

    @SuppressWarnings("GrMethodMayBeStatic")
    private void push(EffectiveDockerPipelineTemplateSingleImageConfiguration configuration) {
        final TextColor c = TextColor.instance
        final Util util = new Util()

        for (String tag : configuration.tags) {
            String name = dockerUtility.tag(configuration.image, tag)

            long start = System.currentTimeMillis()
            util.sh("docker push '${name}'", returnType: 'none')
            long duration = System.currentTimeMillis() - start

            context.log.info("Pushed  \"${c.bold(name)}\" in ${duration / 1000} seconds")
        }
    }

}