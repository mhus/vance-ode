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

/**
 * Where this source's selectors come from.
 *
 * <p>The distinction exists because a server-side taxonomy and an
 * open-ended one need different input affordances. Reporting both as
 * "categories" would leave Vancetope's configuration form with no list for
 * the first kind or an empty one for the second.
 */
public enum OdeSelectorMode {

    /** A finite taxonomy. {@link FeedSource#selectors()} is authoritative. */
    ENUMERABLE,

    /**
     * Open-ended, typed by the reader (tags, accounts). Declare which shapes
     * are accepted in {@link OdeCapabilities#selectorKinds()}.
     */
    FREEFORM,

    /** One single stream — the selector is ignored. */
    NONE
}
