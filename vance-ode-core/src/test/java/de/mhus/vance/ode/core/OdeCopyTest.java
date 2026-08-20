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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The difference from {@code Map.copyOf}: a null value is dropped, not thrown
 * over.
 *
 * <p>These maps come out of somebody else's JSON, where
 * {@code {"expertParams":{"site":null}}} is ordinary. Throwing there turned the
 * endpoint's own NPE into the caller's problem.
 */
class OdeCopyTest {

    @Test
    void map_nullValue_isDroppedNotThrown() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("site", null);
        raw.put("lang", "de");

        assertThatCode(() -> OdeCopy.map(raw)).doesNotThrowAnyException();
        assertThat(OdeCopy.map(raw)).containsExactly(Map.entry("lang", "de"));
    }

    @Test
    void map_keepsInsertionOrder() {
        Map<String, Object> raw = new LinkedHashMap<>();
        for (int i = 0; i < 12; i++) {
            raw.put("k" + i, i);
        }
        assertThat(OdeCopy.map(raw).keySet()).containsExactlyElementsOf(raw.keySet());
    }

    @Test
    void map_isImmutable() {
        Map<String, Object> copy = OdeCopy.map(new LinkedHashMap<>(Map.of("a", 1)));
        assertThatCode(() -> copy.put("b", 2)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void list_nullElement_isDroppedNotThrown() {
        List<String> raw = new ArrayList<>(Arrays.asList("a", null, "b"));

        assertThatCode(() -> OdeCopy.list(raw)).doesNotThrowAnyException();
        assertThat(OdeCopy.list(raw)).containsExactly("a", "b");
    }

    @Test
    void nullInput_isAnEmptyImmutable() {
        assertThat(OdeCopy.map(null)).isEmpty();
        assertThat(OdeCopy.list(null)).isEmpty();
    }
}
