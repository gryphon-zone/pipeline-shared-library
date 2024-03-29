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

class MavenLibraryPipelineConfiguration extends BasePipelineConfiguration {

    MavenLibraryPipelineConfiguration() {
        super()
        this.buildAgent = 'gryphonzone/jenkins-build-agent-maven:java-11'
    }

    String junitResultsPattern = '**/target/surefire-reports/*.xml'

    String jacocoResultsPattern = '**/target/jacoco.exec'

    String mavenDeployArguments = 'clean verify -Dstyle.color=always -V -B'

    String mavenNonDeployArguments = 'clean verify -Dstyle.color=always -V -B'

    /**
     * Whether to release commit to maven central.
     * Requires that the project contains no snapshot dependencies.
     *
     * Ignored if the branch being built does not match the deployableBranchRegex
     */
    boolean automaticallyRelease = false

    void junitResultsPattern(String junitResultsPattern) {
        this.junitResultsPattern = junitResultsPattern
    }

    void jacocoResultsPattern(String jacocoResultsPattern) {
        this.jacocoResultsPattern = jacocoResultsPattern
    }

    void mavenDeployArguments(String mavenDeployArguments) {
        this.mavenDeployArguments = mavenDeployArguments
    }

    void mavenNonDeployArguments(String mavenNonDeployArguments) {
        this.mavenNonDeployArguments = mavenNonDeployArguments
    }

    void automaticallyRelease(boolean automaticallyRelease) {
        this.automaticallyRelease = automaticallyRelease
    }

}