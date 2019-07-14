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

@SuppressWarnings("GrMethodMayBeStatic")
class TextColor {

    public static final TextColor instance = new TextColor()

    private static final String PREFIX = '\u001b['

    private static final String SUFFIX = 'm'

    private static final String BRIGHT_SUFFIX = ';1m'

    private static final String RESET = PREFIX + '0' + SUFFIX

    private static final String BOLD = PREFIX + '1' + SUFFIX
    private static final String DIM = PREFIX + '2' + SUFFIX
    private static final String UNDERLINED = PREFIX + '4' + SUFFIX
    private static final String INVERTED = PREFIX + '7' + SUFFIX

    private static final String BLACK = PREFIX + '30' + SUFFIX
    private static final String RED = PREFIX + '31' + SUFFIX
    private static final String GREEN = PREFIX + '32' + SUFFIX
    private static final String YELLOW = PREFIX + '33' + SUFFIX
    private static final String BLUE = PREFIX + '34' + SUFFIX
    private static final String MAGENTA = PREFIX + '35' + SUFFIX
    private static final String CYAN = PREFIX + '36' + SUFFIX
    private static final String WHITE = PREFIX + '37' + SUFFIX

    private static final String BRIGHT_BLACK = PREFIX + '30' + BRIGHT_SUFFIX
    private static final String BRIGHT_RED = PREFIX + '31' + BRIGHT_SUFFIX
    private static final String BRIGHT_GREEN = PREFIX + '32' + BRIGHT_SUFFIX
    private static final String BRIGHT_YELLOW = PREFIX + '33' + BRIGHT_SUFFIX
    private static final String BRIGHT_BLUE = PREFIX + '34' + BRIGHT_SUFFIX
    private static final String BRIGHT_MAGENTA = PREFIX + '35' + BRIGHT_SUFFIX
    private static final String BRIGHT_CYAN = PREFIX + '36' + BRIGHT_SUFFIX
    private static final String BRIGHT_WHITE = PREFIX + '37' + BRIGHT_SUFFIX

    private static final String BACKGROUND_BLACK = PREFIX + '40' + SUFFIX
    private static final String BACKGROUND_RED = PREFIX + '41' + SUFFIX
    private static final String BACKGROUND_GREEN = PREFIX + '42' + SUFFIX
    private static final String BACKGROUND_YELLOW = PREFIX + '43' + SUFFIX
    private static final String BACKGROUND_BLUE = PREFIX + '44' + SUFFIX
    private static final String BACKGROUND_MAGENTA = PREFIX + '45' + SUFFIX
    private static final String BACKGROUND_CYAN = PREFIX + '46' + SUFFIX
    private static final String BACKGROUND_WHITE = PREFIX + '47' + SUFFIX

    private static final String BACKGROUND_BRIGHT_BLACK = PREFIX + '40' + BRIGHT_SUFFIX
    private static final String BACKGROUND_BRIGHT_RED = PREFIX + '41' + BRIGHT_SUFFIX
    private static final String BACKGROUND_BRIGHT_GREEN = PREFIX + '42' + BRIGHT_SUFFIX
    private static final String BACKGROUND_BRIGHT_YELLOW = PREFIX + '43' + BRIGHT_SUFFIX
    private static final String BACKGROUND_BRIGHT_BLUE = PREFIX + '44' + BRIGHT_SUFFIX
    private static final String BACKGROUND_BRIGHT_MAGENTA = PREFIX + '45' + BRIGHT_SUFFIX
    private static final String BACKGROUND_BRIGHT_CYAN = PREFIX + '46' + BRIGHT_SUFFIX
    private static final String BACKGROUND_BRIGHT_WHITE = PREFIX + '47' + BRIGHT_SUFFIX

    private static String color(String color, String text) {

        if (text == null || text.isEmpty()) {
            return color
        }

        return "${color}${text}${RESET}"
    }

    String terminate(String text) {
        return "${text}${RESET}"
    }

    String bold(String text = null) {
        return color(BOLD, text)
    }

    String dim(String text = null) {
        return color(DIM, text)
    }

    String underlined(String text = null) {
        return color(UNDERLINED, text)
    }

    String inverted(String text = null) {
        return color(INVERTED, text)
    }

    String black(String text = null) {
        return color(BLACK, text)
    }

    String blackBright(String text = null) {
        return color(BRIGHT_BLACK, text)
    }

    String blackBackground(String text = null) {
        return color(BACKGROUND_BLACK, text)
    }

    String blackBrightBackground(String text = null) {
        return color(BACKGROUND_BRIGHT_BLACK, text)
    }

    String red(String text = null) {
        return color(RED, text)
    }

    String redBright(String text = null) {
        return color(BRIGHT_RED, text)
    }

    String redBackground(String text = null) {
        return color(BACKGROUND_RED, text)
    }

    String redBrightBackground(String text = null) {
        return color(BACKGROUND_BRIGHT_RED, text)
    }

    String green(String text = null) {
        return color(GREEN, text)
    }

    String greenBright(String text = null) {
        return color(BRIGHT_GREEN, text)
    }

    String greenBackground(String text = null) {
        return color(BACKGROUND_GREEN, text)
    }

    String greenBrightBackground(String text = null) {
        return color(BACKGROUND_BRIGHT_GREEN, text)
    }

    String yellow(String text = null) {
        return color(YELLOW, text)
    }

    String yellowBright(String text = null) {
        return color(BRIGHT_YELLOW, text)
    }

    String yellowBackground(String text = null) {
        return color(BACKGROUND_YELLOW, text)
    }

    String yellowBrightBackground(String text = null) {
        return color(BACKGROUND_BRIGHT_YELLOW, text)
    }

    String blue(String text = null) {
        return color(BLUE, text)
    }

    String blueBright(String text = null) {
        return color(BRIGHT_BLUE, text)
    }

    String blueBackground(String text = null) {
        return color(BACKGROUND_BLUE, text)
    }

    String blueBrightBackground(String text = null) {
        return color(BACKGROUND_BRIGHT_BLUE, text)
    }

    String magenta(String text = null) {
        return color(MAGENTA, text)
    }

    String magentaBright(String text = null) {
        return color(BRIGHT_MAGENTA, text)
    }

    String magentaBackground(String text = null) {
        return color(BACKGROUND_MAGENTA, text)
    }

    String magentaBrightBackground(String text = null) {
        return color(BACKGROUND_BRIGHT_MAGENTA, text)
    }

    String cyan(String text = null) {
        return color(CYAN, text)
    }

    String cyanBright(String text = null) {
        return color(BRIGHT_CYAN, text)
    }

    String cyanBackground(String text = null) {
        return color(BACKGROUND_CYAN, text)
    }

    String cyanBrightBackground(String text = null) {
        return color(BACKGROUND_BRIGHT_CYAN, text)
    }

    String white(String text = null) {
        return color(WHITE, text)
    }

    String whiteBright(String text = null) {
        return color(BRIGHT_WHITE, text)
    }

    String whiteBackground(String text = null) {
        return color(BACKGROUND_WHITE, text)
    }

    String whiteBrightBackground(String text = null) {
        return color(BACKGROUND_BRIGHT_WHITE, text)
    }
}
