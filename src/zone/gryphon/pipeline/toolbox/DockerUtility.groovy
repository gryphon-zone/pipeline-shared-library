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

import java.util.regex.Pattern

String convertToDockerHubName(String name) {
    return name.replaceAll("\\W", "")
}

String coordinatesFor(String org, String artifact, String tag) {
    return "${org}/${artifact}:${tag}"
}

String tag(String image, String tag) {
    return "${image}:${tag}"
}

String dockerImagesInfoForGivenTags(String image, List<String> tags) {

    if (tags == null || tags.isEmpty()) {
        return ""
    }

    final Util util = new Util()
    final String dockerImageName = "${image}:${tags[0]}"

    // get the unique image ID
    String dockerImageId = util.sh("docker images ${dockerImageName} --format '{{.ID}}' | head -n 1", quiet: true).trim()

    // log the docker image data for all built tags
    String patterns = String.join('|', tags.collect { tag -> Pattern.quote("${tag}") })

    return util.sh("""\
            docker images '${image}' |\
            grep -E 'REPOSITORY|${dockerImageId}' |\
            grep -P '(^REPOSITORY\\s+|${patterns})'\
            """, quiet: true).trim()

}