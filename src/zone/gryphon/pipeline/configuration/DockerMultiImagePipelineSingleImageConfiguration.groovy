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

class DockerMultiImagePipelineSingleImageConfiguration {

    /**
     * [required] Path in the repo to the Dockerfile to use for this build.
     */
    String dockerfile

    /**
     * [required] name of docker artifact to publish.
     */
    String artifact

    /**
     * Build context to use for the docker build command.
     * If not specified, defaults to the directory containing the {@link #dockerfile}.
     */
    String buildContext

    /**
     * Additional hardcoded values to tag the built image as.
     *
     * Only applied if {@link DockerMultiImagePipelineConfiguration#push} is true
     * and the build is deployable.
     */
    List<String> additionalTags = []

    /**
     * Arguments to pass to the "{@code docker build}" command
     */
    String buildArguments = ''

    /**
     * Whether to tag the built image as "latest" or not.
     *
     * Only applicable if {@link DockerMultiImagePipelineConfiguration#push} is true
     * and the build is deployable.
     */
    boolean tagAsLatest = true

    /**
     * Name of the dockerhub organization to public the image to
     */
    String dockerOrganization

    void tagAsLatest(boolean tagAsLatest) {
        this.tagAsLatest = tagAsLatest
    }

    void dockerfile(String dockerfile) {
        this.dockerfile = dockerfile
    }

    void additionalTags(List<String> additionalTags) {
        this.additionalTags = additionalTags
    }

    void buildArguments(String buildArguments) {
        this.buildArguments = buildArguments
    }

    void buildContext(String buildContext) {
        this.buildContext = buildContext
    }

    void dockerOrganization(String dockerOrganization) {
        this.dockerOrganization = dockerOrganization
    }

    void artifact(String artifact) {
        this.artifact = artifact
    }

}