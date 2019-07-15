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

class BasePipelineConfiguration {

    /**
     * Docker image to run pipeline build inside of.
     * Each pipeline will set this to a default value which should contain the tools
     * required to build the artifacts that pipeline targets.
     */
    String buildAgent

    /**
     * Custom job properties
     */
    List jobProperties = []

    /**
     * Build idle timeout in minutes.
     * If there's no activity in the logs for the specified number of minutes, the build is automatically killed.
     */
    int idleTimeout = 5

    /**
     * Regex used to determine if the current branch being built is deployable or not.
     */
    String deployableBranchRegex = 'master'

    /**
     * <p>
     * The name of the "primary" git organization that the git repository lives in.
     * This is used to determine if the current build is coming from the primary organization, or a fork, which is
     * in turn used to calculate whether or not the current build should be considered deployable.
     * </p>
     * <p>
     * <h3>NOTE:</h3>
     * This should <b>not</b> be set in the configuration closure like the other parameters in this class.
     * It will be manually set during configuration parsing, and any value set in the configuration closure
     * will be overwritten.
     * This is why there isn't a convenience setter method without the {@code set} prefix provided for this parameter.
     * </p>
     */
    String deployableOrganization

    void buildAgent(String buildAgent) {
        this.buildAgent = buildAgent
    }

    void jobProperties(List jobProperties) {
        this.jobProperties = jobProperties
    }

    void idleTimeout(int idleTimeout) {
        this.idleTimeout = idleTimeout
    }

    void deployableBranchRegex(String deployableBranchRegex) {
        this.deployableBranchRegex = deployableBranchRegex
    }


}
