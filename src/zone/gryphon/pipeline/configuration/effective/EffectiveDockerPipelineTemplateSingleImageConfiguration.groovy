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

package zone.gryphon.pipeline.configuration.effective

class EffectiveDockerPipelineTemplateSingleImageConfiguration {

    /**
     * Docker image (AKA repository), e.g. "foo/bar"
     */
    String image

    /**
     * Arguments for the docker build command
     */
    String buildArgs

    /**
     * The tags to apply to the image
     */
    List<String> tags

}
