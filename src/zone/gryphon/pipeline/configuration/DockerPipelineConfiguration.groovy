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

class DockerPipelineConfiguration {

    /**
     * Build timeout in minutes; if there's no activity in the logs for this duration of time the build is killed
     */
    int timeoutMinutes = 5

    String buildArgs = '--pull --progress \'plain\''

    String buildContext = '.'

    boolean pushImage = true

    String nodeType = 'docker'

    String deployableBranchRegex = 'master'

    /**
     * Custom job properties
     */
    List jobProperties = []

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


    void jobProperties(List jobProperties) {
        this.jobProperties = jobProperties
    }



}