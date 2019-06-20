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
import zone.gryphon.pipeline.configuration.DockerPipelineConfiguration
import zone.gryphon.pipeline.configuration.ConfigurationHelper

def call(String organization, Closure body) {
    echo "organization: ${organization}"

    DockerPipelineConfiguration config = ConfigurationHelper.configure(body, new DockerPipelineConfiguration())

    echo "config: ${config}"

    echo "properties: ${config.jobProperties}"


    config.jobProperties.each {it ->
        echo "${it.getSymbol()}"
    }

}
