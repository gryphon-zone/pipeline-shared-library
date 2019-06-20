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

class ConfigurationHelper {

    static String buildDiscarder = 'buildDiscarder'

    static <T> T configure(Closure body, T config) {
        body.resolveStrategy = Closure.OWNER_FIRST
        body.delegate = config
        body()
        return config
    }


    static List calculateProperties(List providedProperties) {
        List props = providedProperties ?: []

        int index = props.findIndexOf {it -> it.getSymbol() == buildDiscarder}

        print("Index: ${index}")

        return props
    }

}
