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

package zone.gryphon.pipeline.model

class CheckoutInformation {

    /**
     * The commit hash being checked out.
     */
    String gitCommit

    /**
     * The hash of the commit last built on this branch, if any.
     */
    String gitPreviousCommit

    /**
     * The hash of the commit last successfully built on this branch, if any.
     */
    String gitPreviousSuccessfulCommit

    /**
     * The remote branch name, if any.
     */
    String gitBranch

    /**
     * The local branch name being checked out, if applicable.
     */
    String gitLocalBranch

    /**
     * The remote URL. If there are multiple, will be GIT_URL_1, GIT_URL_2, etc.
     */
    String gitUrl

    /**
     * The configured Git committer name, if any.
     */
    String gitCommitterName

    /**
     * The configured Git author name, if any.
     */
    String gitAuthorName

    /**
     * The configured Git committer email, if any.
     */
    String gitCommitterEmail

    /**
     * The configured Git author email, if any.
     */
    String gitAuthorEmail

}
