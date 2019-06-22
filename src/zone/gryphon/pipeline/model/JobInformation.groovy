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

package zone.gryphon.pipeline.model

class JobInformation {

    /**
     * Organization project belongs to
     */
    String organization

    /**
     * Name of repository/project name
     */
    String repository

    /**
     * Branch being built
     */
    String branch

    /**
     * ID of build being run
     */
    int build

    String toString() {
        return "{\"organization\": \"${organization}\", \"repository\": \"${repository}\", \"branch\": \"${branch}\", \"build\": \"${build}\"}"
    }
}
