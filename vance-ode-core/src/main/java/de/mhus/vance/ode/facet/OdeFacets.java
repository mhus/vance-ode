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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The selection map and its wire form, in one place for both contracts.
 *
 * <p>Two rules that are easy to get subtly wrong and are therefore not left
 * to each controller:
 *
 * <ul>
 *   <li>A selection entry travels as a single repeatable query parameter,
 *       {@code facet=<key>:<value>}, and is split at the <b>first</b> colon.
 *       Values contain colons themselves — {@code m49:142}, {@code iso:SG} —
 *       so splitting anywhere else quietly produces a key nobody declared.
 *   <li>Only declared keys are handed to the source. A key it never claimed
 *       is dropped and logged rather than refused: a reader may be newer than
 *       this end, and an error would turn a filter it can live without into a
 *       broken endpoint.
 * </ul>
 */
public final class OdeFacets {

    private static final Logger log = LoggerFactory.getLogger(OdeFacets.class);

    private OdeFacets() {
        /* static only */
    }

    /**
     * Parse repeated {@code facet=key:value} parameters, keeping only keys
     * the source declared.
     *
     * @param raw      the {@code facet} parameters as received, may be null
     * @param declared the keys from {@link OdeFacet#key()} of the declared facets
     */
    public static Map<String, List<String>> parse(
            @Nullable List<String> raw, Set<String> declared) {
        if (raw == null || raw.isEmpty()) {
            return Map.of();
        }
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (String entry : raw) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            int cut = entry.indexOf(':');
            if (cut <= 0 || cut == entry.length() - 1) {
                log.debug("Ignoring malformed facet parameter '{}' — expected key:value", entry);
                continue;
            }
            String key = entry.substring(0, cut).trim();
            String value = entry.substring(cut + 1).trim();
            if (key.isEmpty() || value.isEmpty()) {
                continue;
            }
            if (!declared.contains(key)) {
                log.debug("Ignoring facet '{}' — not declared by this source", key);
                continue;
            }
            out.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
        }
        return normalize(out);
    }

    /** Immutable copy with blanks dropped, values de-duplicated, order kept. */
    public static Map<String, List<String>> normalize(
            @Nullable Map<String, List<String>> raw) {
        if (raw == null || raw.isEmpty()) {
            return Map.of();
        }
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> e : raw.entrySet()) {
            String key = e.getKey() == null ? null : e.getKey().trim();
            if (key == null || key.isEmpty() || e.getValue() == null) {
                continue;
            }
            Set<String> values = new LinkedHashSet<>();
            for (String v : e.getValue()) {
                if (v != null && !v.isBlank()) {
                    values.add(v.trim());
                }
            }
            if (!values.isEmpty()) {
                out.put(key, List.copyOf(values));
            }
        }
        // unmodifiableMap over the LinkedHashMap, not Map.copyOf: the latter
        // leaves iteration order unspecified and in practice randomises it per
        // JVM run, which would make "order kept" above a false promise — and
        // the order is what a form renders its filters in.
        return Collections.unmodifiableMap(out);
    }

    /**
     * Keep only the keys the source declared, dropping the rest with a log
     * line.
     *
     * <p>The GET side reaches the same result through {@link #parse}; this is
     * for the POST side, where the selection arrives as a map in the body.
     */
    public static Map<String, List<String>> restrictTo(
            @Nullable Map<String, List<String>> selection, Set<String> declared) {
        Map<String, List<String>> normalized = normalize(selection);
        if (normalized.isEmpty()) {
            return Map.of();
        }
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> e : normalized.entrySet()) {
            if (declared.contains(e.getKey())) {
                out.put(e.getKey(), e.getValue());
            } else {
                log.debug("Ignoring facet '{}' — not declared by this source", e.getKey());
            }
        }
        return Collections.unmodifiableMap(out);
    }

    /**
     * The values one level below {@code parent}, or the top level when it is
     * absent.
     *
     * <p>For a facet whose whole tree travelled inline. The controller answers
     * {@code /facets} from that list rather than asking the source again, but it
     * still owes the reader one level: {@code parent} is the question, and
     * returning the flat tree would answer a different one — the reader would
     * render every descendant as a direct child.
     */
    public static List<OdeFacetValue> childrenOf(
            @Nullable List<OdeFacetValue> values, @Nullable String parent) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        String wanted = parent == null || parent.isBlank() ? null : parent.trim();
        List<OdeFacetValue> out = new ArrayList<>();
        for (OdeFacetValue value : values) {
            if (Objects.equals(value.parentId(), wanted)) {
                out.add(value);
            }
        }
        return List.copyOf(out);
    }

    /** The keys a list of declared facets covers. */
    public static Set<String> keysOf(@Nullable List<OdeFacet> facets) {
        if (facets == null || facets.isEmpty()) {
            return Set.of();
        }
        Set<String> out = new LinkedHashSet<>();
        for (OdeFacet facet : facets) {
            out.add(facet.key());
        }
        return Set.copyOf(out);
    }

    /** The facet with this key, or null. */
    public static @Nullable OdeFacet find(@Nullable List<OdeFacet> facets, String key) {
        if (facets == null) {
            return null;
        }
        for (OdeFacet facet : facets) {
            if (facet.key().equals(key)) {
                return facet;
            }
        }
        return null;
    }
}
