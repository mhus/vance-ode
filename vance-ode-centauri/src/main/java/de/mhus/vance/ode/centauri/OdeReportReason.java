/*
 * Copyright 2026 Mike Hummel
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.mhus.vance.ode.centauri;

/** Why an entry is being reported. */
public enum OdeReportReason {

    /** Filed under a category it does not belong to. */
    WRONG_CATEGORY,

    /** Tagged with a language that is not the language of the text. */
    WRONG_LANGUAGE,

    /** The target URL does not resolve, or resolves to something else. */
    BROKEN_LINK,

    /** The same story is already in the stream under another entry. */
    DUPLICATE,

    /** Advertising or content-farm material dressed as an article. */
    SPAM
}
