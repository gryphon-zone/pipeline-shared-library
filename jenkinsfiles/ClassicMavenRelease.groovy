#!/usr/bin/env groovy
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

import zone.gryphon.pipeline.toolbox.ScopeUtility
import zone.gryphon.pipeline.toolbox.Util

import groovy.transform.Field

//@Field final String

void main() {
    final ScopeUtility scope = new ScopeUtility()
    final Util util = new Util()

    String organization = params['Github Organization']
    String repo = params['Repository']
    String branch = params['Branch']

    String release =  params['Release Version']
    String postRelease =  params['Post Release Version']

    String jdk = params['JDK version']


    // run build inside of docker build image
    scope.inDockerImage("maven:3-jdk-${jdk}", args: '-v jenkins-shared-m2-cache:/root/.m2/repository') {
        sshagent(['github-ssh']) {
            util.sh('mkdir -p ~/.ssh && echo StrictHostKeyChecking no >> ~/.ssh/config', quiet: true)

            util.installMavenSettingsFile()


            git(credentialsId: 'github-ssh', url: "git@github.com:${organization}/${repo}.git", branch: branch)

            util.enableGitColor()

            util.sh("""\
            git config user.email 'jenkins@gryphon.zone' && \
            git config user.name 'Jenkins' \
            """.stripIndent().trim(), returnType: 'none')

            scope.withGpgKey('gpg-signing-key-id', 'gpg-signing-key', 'GPG_KEYID') {
                withCredentials([usernamePassword(credentialsId: 'ossrh', usernameVariable: 'OSSRH_USERNAME', passwordVariable: 'OSSRH_PASSWORD')]) {

                    util.sh("""
                    export MAVEN_OPTS='-Djansi.force=true'
                    mvn release:prepare -B -V -Dstyle.color=always -DreleaseVersion='${release}' -DdevelopmentVersion='${postRelease}'
                    mvn release:perform -B -V -Dstyle.color=always -Dossrh.username='${OSSRH_USERNAME}' -Dossrh.password='${OSSRH_PASSWORD}'
                    """, returnType: 'none')
                }
            }

            echo "release: ${release}, post release: ${postRelease}"
        }
    }
}

//noinspection GroovyAssignabilityCheck
properties([
        parameters([
                choice(name: 'Github Organization', choices: ['gryphon-zone', 'chief-tyrol'], description: ''),
                string(name: 'Repository', defaultValue: '', description: '', trim: true),
                string(name: 'Branch', defaultValue: 'master', description: '', trim: true),
                string(name: 'Release Version', defaultValue: '', description: '', trim: true),
                string(name: 'Post Release Version', defaultValue: '', description: '', trim: true),
                choice(name: 'JDK version', choices: ['11', '8', '9', '10', '12', '13', '14'], description: '')

        ])
])

final ScopeUtility scope = new ScopeUtility()

// add support for ANSI color
scope.withColor {

    // add timestamps to build logs
    scope.withTimestamps {

        // no build is allowed to run for more than 1 hour
        scope.withAbsoluteTimeout(60) {

            // allocate executor node
            scope.withExecutor('docker') {

                main()

            }
        }
    }
}