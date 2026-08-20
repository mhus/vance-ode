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
package de.mhus.vance.ode.facet;

import java.util.List;

/**
 * A dimension your source can be filtered by, declared in your capabilities.
 *
 * <p><b>Declaring one is a promise to apply it.</b> There is no „I can label
 * this but not query it" — Vancetope does no local facet filtering, so a
 * facet you declare and ignore is a filter that silently does nothing. If you
 * cannot answer a dimension, leave it out: a reader that selected it will
 * skip you for that request and say so, which is the honest outcome.
 *
 * <p>What that buys you: your entries stay small. You do not ship a place
 * path or a topic list on every item just so the far end can re-check work
 * you already did.
 *
 * <h2>Reserved keys</h2>
 *
 * <p>Two of them carry an agreed value system, and only those mean the same
 * thing across sources:
 *
 * <ul>
 *   <li>{@code origin-place} — where the <em>publisher</em> sits.
 *   <li>{@code subject-place} — what the entry is <em>about</em>.
 *       <p>Both use {@code m49:} above the country level and {@code iso:} at
 *       it ({@code m49:142} Asia, {@code iso:SG} Singapore). Values travel as
 *       a single node; you resolve containment, because you are the one
 *       holding the hierarchy.
 *       <p>They are separate keys on purpose. A wire agency in London filing
 *       from Singapore is both, differently, and a single {@code place} would
 *       let two sources answer two different questions under one checkbox.
 *   <li>{@code origin-topic} / {@code subject-topic} — no agreed vocabulary
 *       yet, so they behave as source-specific keys: your values are yours,
 *       and a reader will not merge them with another source's.
 * </ul>
 *
 * <p>Any other key is yours alone and filters only your streams.
 */
public record OdeFacet(
        String key,
        String label,
        boolean hierarchical,
        List<OdeFacetValue> values,
        boolean lazyChildren) {

    /**
     * How many values may travel inline.
     *
     * <p>The list rides in every capabilities response, is cached for your
     * declared TTL and lands in every configuration form on the other end.
     * Past this, serve the tree level by level — set {@code lazyChildren} and
     * answer {@code /facets?key=…&parent=…}.
     */
    public static final int MAX_INLINE_VALUES = 500;

    public OdeFacet {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("facet key is required");
        }
        key = key.trim();
        if (key.indexOf('.') >= 0) {
            // A reader persists the selection as a map, and MongoDB reads a
            // dot in a map key as a path separator. Cheaper to refuse here
            // than to debug there.
            throw new IllegalArgumentException(
                    "facet key must not contain '.' (was '" + key + "') — use '-'");
        }
        label = label == null || label.isBlank() ? key : label.trim();
        values = values == null ? List.of() : List.copyOf(values);
        if (values.size() > MAX_INLINE_VALUES && !lazyChildren) {
            // Thrown rather than truncated: this is your own declaration, and
            // you can fix it where it happens. The reader's mirror of this
            // record truncates instead — it is parsing somebody else's
            // mistake and must not take the source out of view over it.
            throw new IllegalArgumentException(
                    "facet '" + key + "' declares " + values.size() + " inline values; "
                            + "at most " + MAX_INLINE_VALUES + " may travel inline — "
                            + "set lazyChildren and serve them level by level");
        }
    }

    /** A flat facet whose values are all there is. */
    public static OdeFacet flat(String key, String label, List<OdeFacetValue> values) {
        return new OdeFacet(key, label, false, values, false);
    }

    /** A hierarchical facet whose whole tree fits inline. */
    public static OdeFacet tree(String key, String label, List<OdeFacetValue> values) {
        return new OdeFacet(key, label, true, values, false);
    }
}
