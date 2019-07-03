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

package zone.gryphon.pipeline.toolbox

import groovy.transform.Field
import zone.gryphon.pipeline.model.CheckoutInformation
import zone.gryphon.pipeline.model.JobInformation

@Field
final String silence = '{ set +x; } 2> /dev/null ;'

static String entropy() {
    return UUID.randomUUID().toString().replace("-", "")
}

static String shortHash(CheckoutInformation checkoutInformation) {
    return checkoutInformation.gitCommit.substring(0, 7)
}

def sh(Map map = [:], String script) {
    String label = map['label']
    boolean quiet = map['quiet'] ?: false
    String returnType = map['returnType'] ?: 'stdout'

    boolean returnStdout
    boolean returnStatus

    switch (returnType) {
        case 'none':
            returnStdout = false
            returnStatus = false
            break
        case 'stdout':
            returnStdout = true
            returnStatus = false
            break
        case 'status':
            returnStdout = false
            returnStatus = true
            break
        default:
            throw new IllegalArgumentException("\"returnType\" param must be either \"stdout\" or \"status\", instead got \"${returnType}\"")
    }

    return sh(
            encoding: 'UTF-8',
            returnStatus: returnStatus,
            returnStdout: returnStdout,
            script: quiet ? "${silence} ${script}" : script,
            label: label
    )
}

boolean buildWasTriggerByCommit() {
    List classCauses = [
            'jenkins.branch.BranchEventCause',
            'jenkins.branch.BranchIndexingCause',
            'hudson.triggers.SCMTrigger$SCMTriggerCause',
            'com.cloudbees.jenkins.GitHubPushCause',
            'org.jenkinsci.plugins.github.pullrequest.GitHubPRCause',
            'com.cloudbees.jenkins.plugins.github_pull.GitHubPullRequestCause',
            'com.cloudbees.jenkins.plugins.BitBucketPushCause',
            'hudson.plugins.git.GitStatus$CommitHookCause'
    ]

    List descriptionCauses = [
            'push event to branch'
    ]

    return currentBuild.getBuildCauses().any { cause ->
        String normalized = "${cause['shortDescription']}".toLowerCase()
        boolean matches = classCauses.contains("${cause['_class']}") || descriptionCauses.any {
            normalized.contains("${it}")
        }
//        echo "Raw cause: ${cause}. Commit trigger: ${matches}"
        return matches
    }
}

JobInformation getJobInformation() {
    String info = "${env.JOB_NAME}"

    String[] parts = info.split("/")

    if (parts.length != 3) {
        throw new RuntimeException("Unable to parse job information from \"${info}\", does not follow expected \"organization/artifact/branch\" pattern")
    }

    JobInformation out = new JobInformation()
    out.organization = parts[0]
    out.repository = parts[1]
    out.branch = parts[2]
    out.build = Integer.parseInt("${env.BUILD_NUMBER}")
    return out
}

CheckoutInformation checkoutProject() {
    def vars = checkout scm

    CheckoutInformation out = new CheckoutInformation()
    out.gitCommit = vars.GIT_COMMIT
    out.gitPreviousCommit = vars.GIT_PREVIOUS_COMMIT
    out.gitPreviousSuccessfulCommit = vars.GIT_PREVIOUS_SUCCESSFUL_COMMIT
    out.gitBranch = vars.GIT_BRANCH
    out.gitLocalBranch = vars.GIT_LOCAL_BRANCH
    out.gitUrl = vars.GIT_URL
    out.gitCommitterName = vars.GIT_COMMITTER_NAME
    out.gitAuthorName = vars.GIT_AUTHOR_NAME
    out.gitCommitterEmail = vars.GIT_COMMITTER_EMAIL
    out.gitAuthorEmail = vars.GIT_AUTHOR_EMAIL
    return out
}

void configureMavenSettingsFile() {
    final String file = 'settings.xml'

    String homeDir = this.sh('echo -n "${HOME}"', returnType: 'stdout', quiet: true)

    // writeFile writes into current directory, even if given absolute path
    writeFile(encoding: 'UTF-8', file: file, text: libraryResource(encoding: 'UTF-8', resource: '/m2-settings.xml'))

    // ensure folder exists
    this.sh("mkdir -p ${homeDir}/.m2", quiet: true)

    this.sh("mv ${file} ${homeDir}/.m2/${file}", quiet: true)
}
