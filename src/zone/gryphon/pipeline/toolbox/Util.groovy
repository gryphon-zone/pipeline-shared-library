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

void enableGitColor() {
    log.debug("Enabling colorized git output")

    this.sh("""\
        git config --global color.ui always && \
        git config --global color.branch always && \
        git config --global color.status always \
        """.stripIndent(), quiet: true, returnType: 'none')
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
    final String unknown = 'unknown'

    def vars = checkout scm

    CheckoutInformation out = new CheckoutInformation()

    out.gitBranch = vars.GIT_BRANCH ?: unknown
    out.gitCommit = vars.GIT_COMMIT ?: unknown
    out.gitPreviousCommit = vars.GIT_PREVIOUS_COMMIT ?: unknown
    out.gitPreviousSuccessfulCommit = vars.GIT_PREVIOUS_SUCCESSFUL_COMMIT ?: unknown
    out.gitUrl = vars.GIT_URL ?: unknown

    // although the documentation says all of these should be set, in practice only the above variables are populated
    // TODO see if there's a configuration option to have these populated
    out.gitLocalBranch = vars.GIT_LOCAL_BRANCH ?: unknown
    out.gitCommitterName = vars.GIT_COMMITTER_NAME ?: unknown
    out.gitAuthorName = vars.GIT_AUTHOR_NAME ?: unknown
    out.gitCommitterEmail = vars.GIT_COMMITTER_EMAIL ?: unknown
    out.gitAuthorEmail = vars.GIT_AUTHOR_EMAIL ?: unknown

    return out
}

void configureMavenSettingsFile() {
    log.debug("Installing Maven settings file")

    final String file = 'settings.xml'

    // writeFile always evaluates the path relative to the workspace, even if given absolute path.
    // so instead of trying to calculate the final destination relative to the current workspace,
    // write the file into the workspace and then use sh to move it to the correct location
    writeFile(encoding: 'UTF-8', file: file, text: libraryResource(encoding: 'UTF-8', resource: '/m2-settings.xml'))

    this.sh("""\
         mkdir -p "\${HOME}/.m2" && \
         mv ${file} \${HOME}/.m2/${file}
         """.stripIndent(), quiet: true)
}
