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

import org.jspecify.annotations.Nullable;

/**
 * One selectable stream of an {@link OdeSelectorMode#ENUMERABLE} source.
 *
 * <p>{@code value} is what comes back in {@link OdeItemQuery#selector()} —
 * opaque to Vancetope, so its format is entirely yours. {@code label} is what
 * a person picking a stream sees, and {@code language} lets the configuration
 * form group a large taxonomy without asking twice.
 */
public record OdeSelector(
        String value,
        String label,
        OdeSelectorKind kind,
        @Nullable String language) {

    public OdeSelector {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("selector value is required");
        }
        if (label == null || label.isBlank()) {
            label = value;
        }
        if (kind == null) {
            kind = OdeSelectorKind.CATEGORY;
        }
    }

    public static OdeSelector category(String value, String label) {
        return new OdeSelector(value, label, OdeSelectorKind.CATEGORY, null);
    }

    public static OdeSelector category(String value, String label, String language) {
        return new OdeSelector(value, label, OdeSelectorKind.CATEGORY, language);
    }
}
