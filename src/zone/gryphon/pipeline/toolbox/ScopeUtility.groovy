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

def withRandomAutoCleaningWorkspace(Closure body) {
    withRandomWorkspace {
        try {
            return body()
        } finally {
            echo 'Cleaning workspace'
            cleanWs(notFailBuild: true)
        }
    }
}

def withRandomWorkspace(Closure body) {
    ws("workspace/${Util.entropy()}") {
        return body()
    }
}

def withExecutor(Map map = [:], String label, Closure body) {
    String stageName = map['stageName'] ?: 'Await Executor'

    stage(stageName) {
        node(label) {
            withRandomAutoCleaningWorkspace {
                return body()
            }
        }
    }
}