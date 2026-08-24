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
package de.mhus.vance.ode.jaglan;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * The parameters of a parameterised read — what turns one of your paths into a
 * computed view of itself.
 *
 * <p>A wrapper around {@code Map<String, List<String>>} rather than a plain
 * {@code Map<String, String>}, and that is a deliberate cost. Repeated keys are
 * a real case here, not a curiosity: a multiple-choice input on the reader's
 * side produces {@code tag=a&tag=b}, and a single-valued map would silently
 * keep one of them. Half a chosen set, delivered without complaint, is the
 * failure mode this whole feature is built to avoid.
 *
 * <p>Use {@link #first} for the ordinary single-valued parameter and
 * {@link #all} where several are meaningful.
 */
public record OdeQuery(Map<String, List<String>> parameters) {

    /** No parameters — a plain read. */
    public static final OdeQuery EMPTY = new OdeQuery(Map.of());

    public OdeQuery {
        parameters = parameters == null ? Map.of() : deepCopy(parameters);
    }

    /** Whether any parameter was given at all. */
    public boolean isEmpty() {
        return parameters.isEmpty();
    }

    /** The parameter names, in the order they arrived. */
    public Set<String> names() {
        return parameters.keySet();
    }

    /**
     * The first value of {@code name}, or {@code null} when it was not given.
     *
     * <p>A parameter present without a value (a bare {@code ?refresh}) yields
     * the empty string, which is distinguishable from absence — some APIs use
     * the difference, and flattening it here would take that away.
     */
    public @Nullable String first(String name) {
        List<String> values = parameters.get(name);
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    /** The first value of {@code name}, or {@code fallback} when absent. */
    public String first(String name, String fallback) {
        String value = first(name);
        return value == null ? fallback : value;
    }

    /** Every value of {@code name}; empty when it was not given. */
    public List<String> all(String name) {
        return parameters.getOrDefault(name, List.of());
    }

    /**
     * Defensive copy that <b>keeps insertion order</b>.
     *
     * <p>Not {@code Map.copyOf}: its iteration order is unspecified and
     * randomised per JVM run, which would quietly falsify {@link #names()} and
     * break any source deriving a cache key from the parameters — the key
     * would change on every restart, and the contract's "same parameters, same
     * answer" with it.
     */
    private static Map<String, List<String>> deepCopy(Map<String, List<String>> source) {
        Map<String, List<String>> copy = new LinkedHashMap<>();
        source.forEach((key, values) ->
                copy.put(key, values == null ? List.of() : List.copyOf(values)));
        return Collections.unmodifiableMap(copy);
    }
}
