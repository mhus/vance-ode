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

import org.jspecify.annotations.Nullable;

/**
 * One selectable value of an {@link OdeFacet}.
 *
 * <p>{@code id} is what comes back in the query and is entirely yours —
 * Vancetope never interprets it, with one exception: for the reserved
 * {@code *-place} keys the {@code m49:} / {@code iso:} prefixes are the
 * agreed vocabulary, because a shared key with two value systems is a shared
 * name and nothing else.
 *
 * <p>{@code label} is what a person picking it reads, in whatever language
 * you wrote it — it is shown as-is and never translated. {@code parentId}
 * names the value one level up for a hierarchical facet; the top level has
 * none. The tree travels flat, so it can be indexed in one pass instead of
 * walked.
 */
public record OdeFacetValue(String id, String label, @Nullable String parentId) {

    public OdeFacetValue {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("facet value id is required");
        }
        id = id.trim();
        label = label == null || label.isBlank() ? id : label.trim();
        parentId = parentId == null || parentId.isBlank() ? null : parentId.trim();
    }

    /** A value at the top level. */
    public static OdeFacetValue of(String id, String label) {
        return new OdeFacetValue(id, label, null);
    }
}
