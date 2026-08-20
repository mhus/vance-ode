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

import de.mhus.vance.ode.facet.OdeFacets;
import de.mhus.vance.ode.inbound.OdeAuthService;
import de.mhus.vance.ode.inbound.OdeCaller;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * One search, as it reaches a {@link SearchSource}.
 *
 * <p>Everything here has already been checked against
 * {@link OdeSearchCapabilities}: the modality and tier are ones this source
 * declared, and {@link #maxResults} is clamped to what it said it would serve.
 * An implementation does not have to re-validate them.
 *
 * <p><b>No reader field.</b> See the package documentation — a search query is
 * not a reading history, and the field is absent so that nobody can start
 * depending on it without the decision being made first.
 *
 * @param query        the search terms; never blank.
 * @param modality     kind of result being asked for.
 * @param tier         {@link OdeSearchTier#EXPERT} only if this source declared
 *                     it; otherwise {@link #expertParams} is guaranteed empty.
 * @param maxResults   upper bound on hits to return. Returning fewer is normal
 *                     and returning none is not an error.
 * @param locale       BCP-47 language tag of the asker's preference
 *                     ({@code de-DE}), or null. A hint, not a filter — a source
 *                     that has nothing in that language should return what it
 *                     has rather than nothing.
 * @param expertParams the source's own filter vocabulary, passed through
 *                     untouched. Unknown keys should be ignored, not refused:
 *                     the caller cannot know this source's schema and a refusal
 *                     costs the whole query.
 * @param caller       whose token got this request in, when an
 *                     {@link OdeAuthService} named it — a customer or a
 *                     contract, <b>never a person</b>. Null on an endpoint
 *                     without one. Narrow what you search by it if your
 *                     licensing says so; do not use it to reorder results, and
 *                     do not let {@code capabilities()} depend on it, which
 *                     both ends cache.
 */
public record OdeSearchQuery(
        String query,
        OdeSearchModality modality,
        OdeSearchTier tier,
        int maxResults,
        @Nullable String locale,
        Map<String, Object> expertParams,
        /**
         * Facet selection, {@code key -> values} — conjunction across keys,
         * disjunction within one. Only keys you declared in
         * {@link OdeSearchCapabilities#facets()} arrive here, so there is
         * nothing to validate: what is in the map, you promised to answer.
         *
         * <p>A hierarchical facet hands you one node ({@code m49:142});
         * resolving containment is yours, because you hold the hierarchy.
         */
        Map<String, List<String>> facets,
        @Nullable OdeCaller caller) {

    /** The same query without facets. */
    public OdeSearchQuery(
            String query,
            OdeSearchModality modality,
            OdeSearchTier tier,
            int maxResults,
            @Nullable String locale,
            Map<String, Object> expertParams,
            @Nullable OdeCaller caller) {
        this(query, modality, tier, maxResults, locale, expertParams, Map.of(), caller);
    }

    public OdeSearchQuery {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query is required");
        }
        if (modality == null) {
            throw new IllegalArgumentException("modality is required");
        }
        tier = tier == null ? OdeSearchTier.NORMAL : tier;
        expertParams = expertParams == null ? Map.of() : Map.copyOf(expertParams);
        facets = OdeFacets.normalize(facets);
        if (maxResults <= 0) {
            throw new IllegalArgumentException("maxResults must be > 0, was " + maxResults);
        }
    }
}
