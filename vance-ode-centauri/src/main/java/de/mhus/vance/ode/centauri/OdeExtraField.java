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
 * One {@link OdeItem#extras()} key worth showing a person, and what to call
 * it.
 *
 * <p>Without this the reader has to guess, and guessing means hardcoding one
 * source's vocabulary: a card that looks for {@code originPlace} finds nothing
 * at a Mastodon instance and misses its {@code boosts} entirely. You know your
 * keys; nobody else does.
 *
 * <p>Declared in {@link OdeCapabilities} rather than on the item, because a
 * label repeated on every entry is the same label twenty times per page. The
 * list order is the display order, and an empty list means „show none of it" —
 * the same rule the signal buttons follow. {@code extras} itself stays free:
 * this says what is worth reading, not what the values are or that they will
 * keep their shape.
 *
 * <p>A key that only exists on the detail ({@code item(id)}) is declared like
 * any other. The reader shows what an entry actually carries, so nothing has
 * to say „detail only".
 */
public record OdeExtraField(String key, String label) {

    public OdeExtraField {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("extra field key is required");
        }
        key = key.trim();
        label = label == null || label.isBlank() ? key : label.trim();
    }

    public static OdeExtraField of(String key, String label) {
        return new OdeExtraField(key, label);
    }
}
