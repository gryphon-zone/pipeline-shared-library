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
@Library('gryphon-zone/pipeline-shared-library@master') _

//noinspection GroovyAssignabilityCheck
properties([
        parameters([
                choice(name: 'Github Organization', choices: ['gryphon-zone', 'chief-tyrol'], description: ''),
                string(name: 'Repository', defaultValue: '', description: '', trim: false),
                string(name: 'Release Version', defaultValue: '', description: '', trim: true),
                string(name: 'Post Release Version', defaultValue: '', description: '', trim: true),
                choice(name: 'JDK version', choices: ['11', '8', '9', '10', '12', '13', '14'], description: '')

        ])
])
