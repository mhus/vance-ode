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
package de.mhus.vance.ode.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Defensive copies for the record constructors on the wire boundary.
 *
 * <p>{@code Map.copyOf} and {@code List.copyOf} throw
 * {@link NullPointerException} on a null value or element, and every one of
 * these records is built from JSON somebody else wrote. A body as ordinary as
 * {@code {"expertParams":{"site":null}}} is well-formed JSON, and answering it
 * with a 500 — or, once the exception passed the old handler, with a 400 whose
 * message was the literal string {@code "null"} — is the endpoint reporting its
 * own bug as the caller's.
 *
 * <p>Nulls are dropped rather than refused. An absent value and an explicit
 * {@code null} are the same statement in a pass-through map, and a source is
 * asked to ignore keys it does not know anyway — so the entry that carries no
 * value simply does not travel.
 *
 * <p>Order is kept, which {@code Map.copyOf} does not promise: these maps reach
 * forms and log lines where a stable order is the difference between a diff and
 * a re-read.
 */
public final class OdeCopy {

    private OdeCopy() {
        /* static only */
    }

    /** Immutable copy of {@code raw}, null entries dropped, order kept. */
    public static <K, V> Map<K, V> map(@Nullable Map<K, V> raw) {
        if (raw == null || raw.isEmpty()) {
            return Map.of();
        }
        Map<K, V> out = new LinkedHashMap<>();
        for (Map.Entry<K, V> e : raw.entrySet()) {
            if (e.getKey() != null && e.getValue() != null) {
                out.put(e.getKey(), e.getValue());
            }
        }
        return Collections.unmodifiableMap(out);
    }

    /** Immutable copy of {@code raw}, null elements dropped, order kept. */
    public static <T> List<T> list(@Nullable List<T> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<T> out = new ArrayList<>(raw.size());
        for (T item : raw) {
            if (item != null) {
                out.add(item);
            }
        }
        return Collections.unmodifiableList(out);
    }
}
