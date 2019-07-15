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

import zone.gryphon.pipeline.model.JobInformation
import zone.gryphon.pipeline.toolbox.TextColor


def <T extends BasePipelineConfiguration> T configure(String organization, Closure body, T config) {
    body.resolveStrategy = Closure.OWNER_FIRST
    body.delegate = config
    body()
    config.deployableOrganization = organization
    return config
}

@SuppressWarnings("GrMethodMayBeStatic")
boolean isDeployable(BasePipelineConfiguration configuration, JobInformation info) {
    // branch is deployable if it matches the regex AND it's not from a fork
    return (configuration.deployableOrganization == info.organization) && (info.branch.matches(configuration.deployableBranchRegex))
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

void printConfiguration(Map effectiveConfiguration) {
    Map<String, String> singleLineEntries = [:]
    Map<String, String> multiLineEntries = [:]

    // maximum key length for single line key
    int maxKeyLength = 0

    for (Map.Entry entry : effectiveConfiguration.entrySet()) {
        String key = String.valueOf(entry.getKey())
        String value = String.valueOf(entry.getValue())

        if (value.contains('\n')) {
            multiLineEntries.put(key, value)
        } else {
            maxKeyLength = Math.max(maxKeyLength, key.length())
            singleLineEntries.put(key, value)
        }
    }

    final TextColor c = TextColor.instance
    final List sortedSingleLineKeys = singleLineEntries.keySet().toSorted()
    final List sortedMultiLineKeys = multiLineEntries.keySet().toSorted()

    String configurationMessage = ''
    configurationMessage += c.cyan('-' * 60) + '\n'
    configurationMessage += c.cyan('Effective Configuration:') + '\n'
    configurationMessage += c.cyan('----------------------- ') + '\n'

    for (String key : sortedSingleLineKeys) {
        String value = singleLineEntries.get(key)

        int paddingLength = maxKeyLength - key.length()
        String padding = paddingLength > 0 ? (' ' * paddingLength) : ''

        configurationMessage += c.green(key + padding + ' :') + ' ' + c.blue(value) + '\n'
    }

    for (String key : sortedMultiLineKeys) {
        String value = multiLineEntries.get(key)
        configurationMessage += c.green(key + ':') + '\n'
        configurationMessage += value.split("\n").collect({ c.blue(it) }).join("\n") + '\n'
    }

    configurationMessage += c.cyan('-' * 60)

    echo(configurationMessage)
}


/**
 * Converts a list of Jenkins job properties into a printable string
 * @param properties
 * @return
 */
static String convertPropertiesToPrintableForm(List properties) {
    final String indent = ' ' * 4
    List out = []
    out.add('[')

    int propertyCount = properties.size()
    properties.eachWithIndex { property, propertyIndex ->
        final String symbol = "${property.symbol}"

        // special handling to unwrap parameters property
        if (symbol == 'parameters') {
            out.add("${indent}@parameters([")
            property.arguments.values().eachWithIndex { parameters, parametersIndex ->
                int size = parameters.size()
                parameters.eachWithIndex { parameter, index ->
                    out.add("${indent}${indent}${parameter}${index == size - 1 ? '' : ','}")
                }
            }
            out.add("${indent}])")
        } else {
            // other properties are handled as normal
            out.add("${indent}${property}")
        }

        if (propertyIndex < propertyCount - 1) {
            out[-1] = "${out[-1]},"
        }
    }
    out.add(']')

    return String.join("\n", out).replace('<anonymous>=', '')
}

