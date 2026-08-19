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
package de.mhus.vance.ode.zarniwoop;

/**
 * Whether a hit's body travels with the hit or is fetched on demand.
 *
 * <p>{@link #EMBED_TEXT} is the normal case and it makes the content endpoint
 * unnecessary: a source with short bodies (abstracts, teasers, summaries) simply
 * ships them. {@link #STASH_ON_DEMAND} is for expensive full texts — the caller
 * fetches those only for the hits it actually keeps.
 */
public enum OdeContentInline {

    /** Body is in {@code OdeHitContent.text}; no second request. */
    EMBED_TEXT,

    /** Body must be fetched from {@code GET {path}/content/{contentId}}. */
    STASH_ON_DEMAND
}
