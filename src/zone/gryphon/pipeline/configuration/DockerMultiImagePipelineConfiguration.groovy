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

package zone.gryphon.pipeline.configuration

class DockerMultiImagePipelineConfiguration extends BasePipelineConfiguration {

    DockerMultiImagePipelineConfiguration() {
        super()
        this.buildAgent = 'gryphonzone/jenkins-build-agent-docker:latest'
    }

    /**
     * Whether to push built images or not; only applicable of the build is deployable
     */
    boolean push = true

    /**
     * ID of username/password credentials to use to log into docker for pushing images.
     */
    String dockerCredentialsId = 'docker'

    /**
     * Arguments applied to the "{@code docker build}" command for every image built.
     */
    String globalBuildArguments = '--pull --progress \'plain\''

    /**
     * Configuration for the images to build.
     * @see DockerMultiImagePipelineSingleImageConfiguration
     */
    List<Closure> images = []

    void push(boolean push) {
        this.push = push
    }

    void dockerCredentialsId(String dockerCredentialsId) {
        this.dockerCredentialsId = dockerCredentialsId
    }

    void globalBuildArguments(String globalBuildArguments) {
        this.globalBuildArguments = globalBuildArguments
    }

    void images(List<Closure> images) {
        this.images = images
    }

}