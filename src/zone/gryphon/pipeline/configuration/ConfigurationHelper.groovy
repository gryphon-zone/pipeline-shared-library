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


def <T> T configure(Closure body, T config) {
    body.resolveStrategy = Closure.OWNER_FIRST
    body.delegate = config
    body()
    return config
}


List calculateProperties(List providedProperties, Object... additionalProps) {
    List props = []

    props.addAll(additionalProps)

    if (providedProperties) {
        props.addAll(providedProperties)
    }

    int index = props.findIndexOf { it -> it.getSymbol() == 'buildDiscarder' }

    // no build discarder, install default
    if (index < 0) {
        props.add(buildDiscarder(logRotator(artifactDaysToKeepStr: '1', artifactNumToKeepStr: '1', daysToKeepStr: '90', numToKeepStr: '100')))
    }

    return props
}


String toPrintableForm(List properties) {
    final String indent = ' ' * 4
    List out = []

    properties.each { property ->

        if ("${property.symbol}" == "parameters") {
            echo "${property.symbol}"

            property.arguments.values().each { val ->
                echo "val: ${val}"
            }
        }

        out.add("${property}")
    }

    return String.join("\n", out).replace('<anonymous>=', '')
}

