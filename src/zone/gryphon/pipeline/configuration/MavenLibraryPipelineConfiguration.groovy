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

class MavenLibraryPipelineConfiguration {

    /**
     * Build timeout in minutes; if there's no activity in the logs for this duration of time the build is killed
     */
    int timeoutMinutes = 5

    /**
     * Docker image to run maven build in
     */
    String buildAgent = 'gryphonzone/java:11-jdk'

    /**
     * Regex for which branches are considered "releasable".
     * Non releasable branches will not have their artifacts pushed remotely
     */
    String deployableBranchRegex = 'master'

    String mavenDeployArguments = 'clean verify -Dstyle.color=always -V -B'

    String mavenNonDeployArguments = 'clean verify -Dstyle.color=always -V -B'

    /**
     * Whether to release commit to maven central.
     * Requires project contain no snapshot dependencies.
     *
     * Ignored if the branch being built does not match the deployableBranchRegex
     */
    boolean automaticallyRelease = true

    /**
     * Custom job properties
     */
    List jobProperties = []

    void jobProperties(List jobProperties) {
        this.jobProperties = jobProperties
    }



}