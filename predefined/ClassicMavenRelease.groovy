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

import groovy.transform.Field
import zone.gryphon.pipeline.toolbox.ScopeUtility
import zone.gryphon.pipeline.toolbox.Util

@Field final String PARAM_ORG = 'Github Organization'
@Field final String PARAM_REPO = 'Repository'
@Field final String PARAM_BRANCH = 'Branch'
@Field final String PARAM_RELEASE_VERSION = 'Release Version'
@Field final String PARAM_NEXT_VERSION = 'Post Release Version'
@Field final String PARAM_JDK_VERSION = 'JDK version'

@Field final List<String> WHITELISTED_ORGS = ['gryphon-zone', 'chief-tyrol']

boolean isBlank(String input) {
    return input == null || input == '' || input.trim() == ''
}

void main() {
    final ScopeUtility scope = new ScopeUtility()
    final Util util = new Util()
    final String mvn = 'mvn -B -V -Dstyle.color=always'

    String releaseVersionArgument
    String postReleaseVersionArgument

    String organization = params[PARAM_ORG]
    String repo = params[PARAM_REPO]
    String branch = params[PARAM_BRANCH]

    String releaseVersion = params[PARAM_RELEASE_VERSION]
    String postReleaseVersion = params[PARAM_NEXT_VERSION]

    String jdk = params[PARAM_JDK_VERSION]

    if (!WHITELISTED_ORGS.contains(organization)) {
        error("Unable to release from organization \"${organization}\"")
        return
    }

    if (isBlank(repo)) {
        error("Github repository must be specified")
        return
    }

    if (isBlank(branch)) {
        error("Branch must be specified")
        return
    }

    if (isBlank(jdk)) {
        error("JDK version must be specified")
        return
    }

    try {
        jdk.toInteger()
    } catch (Exception e) {
        error("Invalid JDK version, must be an integer: \"${jdk}\"")
        throw e
    }

    if (isBlank(releaseVersion)) {
        releaseVersionArgument = ''
    } else if (releaseVersion.contains("'")) {
        error("Invalid release version, contains invalid characters: \"${releaseVersion}")
        return
    } else {
        releaseVersionArgument = "-DreleaseVersion='${releaseVersion}'"
    }

    if (isBlank(postReleaseVersion)) {
        postReleaseVersionArgument = ''
    } else if (postReleaseVersion.contains("'")) {
        error("Invalid post-release version, contains invalid characters: \"${postReleaseVersion}")
        return
    } else if (!postReleaseVersion.endsWith('-SNAPSHOT')) {
        error("Invalid post-release version, must end in \"-SNAPSHOT\": \"${postReleaseVersion}")
        return
    } else {
        postReleaseVersionArgument = "-DdevelopmentVersion='${postReleaseVersion}'"
    }

    currentBuild.displayName = "${organization}/${repo}@${releaseVersion} (#${env.BUILD_NUMBER})"

    // run build inside of docker build image
    scope.inDockerImage("maven:3-jdk-${jdk}", args: '-v jenkins-shared-m2-cache:/root/.m2/repository') {
        stage('Maven Release') {
            sshagent(['github-ssh']) {
                scope.withGpgKey('gpg-signing-key-id', 'gpg-signing-key', 'GPG_KEYID') {
                    withCredentials([usernamePassword(credentialsId: 'ossrh', usernameVariable: 'OSSRH_USERNAME', passwordVariable: 'OSSRH_PASSWORD')]) {

                        util.sh('mkdir -p ~/.ssh && echo StrictHostKeyChecking no >> ~/.ssh/config', quiet: true)

                        util.installMavenSettingsFile()

                        git(credentialsId: 'github-ssh', url: "git@github.com:${organization}/${repo}.git", branch: branch)

                        util.sh("""
                        export MAVEN_OPTS='-Djansi.force=true'
                        git config --global user.email 'jenkins@gryphon.zone'
                        git config --global user.name 'Jenkins'
                        ${mvn} release:prepare ${releaseVersionArgument} ${postReleaseVersionArgument} -Darguments='-Dstyle.color=always'
                        ${mvn} release:perform -Dossrh.username='${OSSRH_USERNAME}' -Dossrh.password='${OSSRH_PASSWORD}' -Darguments='-Dstyle.color=always -DskipTests=true'
                        git status
                        """, returnType: 'none')
                    }
                }
            }
        }
    }
}

//noinspection GroovyAssignabilityCheck
properties([
        parameters([
                choice(name: PARAM_ORG, choices: WHITELISTED_ORGS, description: ''),
                string(name: PARAM_REPO, defaultValue: '', description: '', trim: true),
                string(name: PARAM_BRANCH, defaultValue: 'master', description: '', trim: true),
                string(name: PARAM_RELEASE_VERSION, defaultValue: '', description: '', trim: true),
                string(name: PARAM_NEXT_VERSION, defaultValue: '', description: '', trim: true),
                choice(name: PARAM_JDK_VERSION, choices: ['11', '8', '9', '10', '12', '13', '14'], description: '')

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