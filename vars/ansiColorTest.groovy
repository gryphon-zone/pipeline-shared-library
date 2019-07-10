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

import zone.gryphon.pipeline.toolbox.ScopeUtility
import zone.gryphon.pipeline.toolbox.TextColor

def call(String palette = null) {
    final ScopeUtility scope = new ScopeUtility()

    scope.withTimestamps {
        scope.withAbsoluteTimeout(5) {
            final TextColor c = TextColor.instance
            final String terminator = ('#' * 10)
            final String message = "${terminator}    The quick brown fox jumps over the lazy dog. 0123456789 !@#\$%^&*()[]{}<>,./?|    ${terminator}"

            palette = palette ?: 'xterm'

            echo """\
            ${terminator}
            testing with palette "${palette}"
            ${terminator}
            """.stripIndent()

            // add support for ANSI color
            scope.withColor(palette) {
                echo """\
                black:                   ${c.black(message)}
                blackBright:             ${c.blackBright(message)}
                blackBackground:         ${c.blackBackground(message)}
                blackBrightBackground:   ${c.blackBrightBackground(message)}

                red:                     ${c.red(message)}
                redBright:               ${c.redBright(message)}
                redBackground:           ${c.redBackground(message)}
                redBrightBackground:     ${c.redBrightBackground(message)}

                green:                   ${c.green(message)}
                greenBright:             ${c.greenBright(message)}
                greenBackground:         ${c.greenBackground(message)}
                greenBrightBackground:   ${c.greenBrightBackground(message)}

                yellow:                  ${c.yellow(message)}
                yellowBright:            ${c.yellowBright(message)}
                yellowBackground:        ${c.yellowBackground(message)}
                yellowBrightBackground:  ${c.yellowBrightBackground(message)}

                blue:                    ${c.blue(message)}
                blueBright:              ${c.blueBright(message)}
                blueBackground:          ${c.blueBackground(message)}
                blueBrightBackground:    ${c.blueBrightBackground(message)}

                magenta:                 ${c.magenta(message)}
                magentaBright:           ${c.magentaBright(message)}
                magentaBackground:       ${c.magentaBackground(message)}
                magentaBrightBackground: ${c.magentaBrightBackground(message)}

                cyan:                    ${c.cyan(message)}
                cyanBright:              ${c.cyanBright(message)}
                cyanBackground:          ${c.cyanBackground(message)}
                cyanBrightBackground:    ${c.cyanBrightBackground(message)}

                white:                   ${c.white(message)}
                whiteBright:             ${c.whiteBright(message)}
                whiteBackground:         ${c.whiteBackground(message)}
                whiteBrightBackground:   ${c.whiteBrightBackground(message)}
                """.stripIndent()
            }
        }
    }
}
