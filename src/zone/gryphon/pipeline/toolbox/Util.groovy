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

import zone.gryphon.pipeline.model.CheckoutInformation
import zone.gryphon.pipeline.model.JobInformation

def withTimestamps(Closure body) {
    timestamps {
        body()
    }
}

def withColor(String color = 'xterm', Closure body) {
    ansiColor(color) {
        body()
    }
}

def withAbsoluteTimeout(minutes = 60, Closure body) {
    timeout(time: minutes) {
        body()
    }
}

def withTimeout(minutes = 10, Closure body) {
    timeout(activity: true, time: minutes) {
        body()
    }
}

static String entropy() {
    return UUID.randomUUID().toString().replace("-", "")
}

def withRandomWorkspace(Closure body) {
    ws(entropy()) {
        body()
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