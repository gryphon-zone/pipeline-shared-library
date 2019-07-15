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

def withTimestamps(Closure body) {
    timestamps {
        return body()
    }
}

def withColor(String color = 'xterm', Closure body) {
    ansiColor(color) {
        return body()
    }
}

def withAbsoluteTimeout(minutes = 60, Closure body) {
    timeout(time: minutes) {
        return body()
    }
}

def withTimeout(minutes = 10, Closure body) {
    timeout(activity: true, time: minutes) {
        return body()
    }
}

def withRandomWorkspace(Closure body) {
    ws("workspace/${Util.entropy()}") {
        return body()
    }
}

def withRandomAutoCleaningWorkspace(Closure body) {
    withRandomWorkspace {
        try {
            return body()
        } finally {
            log.info('Cleaning workspace')
            cleanWs(notFailBuild: true)
        }
    }
}

def withExecutor(Map map = [:], String label, Closure body) {
    String stageName = map['stageName'] ?: 'Await Executor'

    stage(stageName) {

        log.info("Awaiting node matching '${label}'")

        node(label) {
            withRandomAutoCleaningWorkspace {
                return body()
            }
        }
    }
}

def inDockerImage(Map map = [:], String dockerImage, Closure body) {
    String stageName = map['stageName'] ?: 'Await Build Agent'
    String args = map['args'] ?: '-v /var/run/docker.sock:/var/run/docker.sock'

    stage(stageName) {
        docker.image(dockerImage).inside(args) {
            return body()
        }
    }
}

def withDockerAuthentication(String credentialsId, Closure body) {
    final Util util = new Util()

    try {

        withCredentials([usernamePassword(credentialsId: credentialsId, passwordVariable: 'password', usernameVariable: 'username')]) {
            util.sh("echo \"${password}\" | docker login -u \"${username}\" --password-stdin", quiet: true)
        }

        return body()

    } finally {

        try {
            util.sh("docker logout", quiet: true)
        } catch (Exception e) {
            log.warn("Failed to log out of docker. ${e.class.simpleName}: ${e.message}")
        }

        try {
            util.sh("rm -f \"${HOME}/.docker/config.json\"", quiet: true)
        } catch (Exception e) {
            log.warn("Failed to remove docker credentials file. ${e.class.simpleName}: ${e.message}")
        }
    }
}

def withGpgKey(String keyId, String signingKeyId, String keyIdEnvVariable, Closure body) {
    withCredentials([string(credentialsId: keyId, variable: keyIdEnvVariable), file(credentialsId: signingKeyId, variable: 'GPG_SIGNING_KEY')]) {
        final Util util = new Util()
        log.debug('Importing GPG key')
        util.sh("gpg --batch --yes --import ${GPG_SIGNING_KEY}", returnType: 'none', quiet: true)
        log.info('Successfully imported GPG key') // previous line throws an error if it fails

        try {
            return body()
        } finally {
            log.debug('Deleting GPG key')
            String fingerprint = util.sh("IFS=\$'\\n' gpg --list-keys --keyid-format=none ${env[keyIdEnvVariable]} | grep -E '^\\s*[a-fA-F0-9]+\\s*\$' | tr -d '[:blank:]'", quiet: true)
            util.sh("gpg --batch --yes --delete-secret-and-public-key ${fingerprint}", quiet: true)
            log.info('Deleted GPG key')
        }
    }
}

/**
 *
 * Supported named configuration options:
 * <table>
 *      <tr><th>Option</th><th>Description</th></tr>
 *      <tr><td><pre>executor</pre></td><td>Label for executor (node) to allocate</td><tr>
 * </table
 * @param configuration Named configuration options
 * @param body The closure to run
 * @return The return value of the closure
 */
def withStandardPipelineWrappers(Map configuration = [:], Closure body) {
    String executor = "${configuration['executor'] ?: 'docker'}"

    long start = System.currentTimeMillis()

    // add support for ANSI color
    this.withColor {

        // add timestamps to build logs
        this.withTimestamps {

            try {

                log.info('Enabling default build timeout')

                // no build is allowed to run for more than 1 hour
                this.withAbsoluteTimeout(60) {

                    // allocate executor node
                    this.withExecutor(executor) {

                        return body()

                    }
                }

            } finally {
                long durationMs = System.currentTimeMillis() - start
                log.info("Pipeline ${env.BUILD_URL} completed in ${durationMs / 1000} seconds")

            }
        }
    }
}