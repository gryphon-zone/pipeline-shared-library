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

import zone.gryphon.pipeline.toolbox.TextColor

private boolean isColorSupported() {
    // ansi color plugin sets the TERM environment variable when color is enabled
    return env.TERM != null
}

enum Level {
    DEBUG,
    INFO,
    WARN,
    ERROR
}

private void logMessage(Level level, String message) {
    TextColor color = TextColor.instance
    String basePrefix = "${level.name()}"
    String prefix

    if (isColorSupported()) {
        String l
        switch (level) {
            case Level.INFO:
                l = color.blue(basePrefix)
                break
            case Level.WARN:
                l = color.yellow(basePrefix)
                break
            case Level.ERROR:
                l = color.red(basePrefix)
                break
            default:
                l = "${basePrefix}"
        }

        prefix = color.bold("[${l}]")
    } else {
        prefix = "[${basePrefix}]"
    }

    echo("${prefix} ${message}")
}

void info(String message, Object... parts) {
    logMessage(Level.INFO, String.format(message, parts))
}

void warn(String message, Object... parts) {
    logMessage(Level.WARN, String.format(message, parts))
}

void error(String message, Object... parts) {
    logMessage(Level.ERROR, String.format(message, parts))
}
