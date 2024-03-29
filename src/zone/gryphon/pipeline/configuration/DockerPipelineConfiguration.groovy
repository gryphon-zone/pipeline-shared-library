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

class DockerPipelineConfiguration extends BasePipelineConfiguration {

    DockerPipelineConfiguration() {
        super()
        this.buildAgent = 'gryphonzone/jenkins-build-agent-docker:latest'
    }

    String buildArgs = '--pull --progress \'plain\''

    String dockerfile = 'Dockerfile'

    List<String> tags = []

    String buildContext = null

    boolean pushImage = true

    boolean tagAsLatest = true

    /**
     * Name of the dockerhub organization to public the image to
     */
    String dockerOrganization = null

    /**
     * name of docker artifact to publish.
     */
    String dockerArtifact = null

    /**
     * ID of username/password credentials to use to log into docker
     */
    String dockerCredentialsId = 'docker'

    String version = '1.0'

}