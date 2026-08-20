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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The two promises the selection helpers make that are easy to break. */
class OdeFacetsTest {

    @Test
    void normalize_keepsTheOrderItPromises() {
        // Map.copyOf randomises iteration order per JVM run, and this map ends
        // up rendering a form. Enough keys that a randomised order would not
        // land on the input order by chance.
        Map<String, List<String>> raw = new LinkedHashMap<>();
        for (int i = 0; i < 12; i++) {
            raw.put("key-" + i, List.of("v" + i));
        }

        assertThat(OdeFacets.normalize(raw).keySet())
                .containsExactlyElementsOf(raw.keySet());
    }

    @Test
    void normalize_dropsBlanksAndDeduplicates() {
        Map<String, List<String>> raw = new LinkedHashMap<>();
        raw.put(" topic ", new ArrayList<>(List.of("sport", " sport ", "  ", "news")));
        raw.put("empty", new ArrayList<>(List.of("   ")));

        assertThat(OdeFacets.normalize(raw))
                .containsOnlyKeys("topic")
                .containsEntry("topic", List.of("sport", "news"));
    }

    @Test
    void childrenOf_withoutParent_isTheTopLevel() {
        assertThat(OdeFacets.childrenOf(tree(), null))
                .extracting(OdeFacetValue::id)
                .containsExactly("m49:142", "m49:150");
    }

    @Test
    void childrenOf_withParent_isOneLevelNotTheSubtree() {
        // iso:SG sits under m49:142; the district below it must not come along,
        // or a reader renders a grandchild as a direct child.
        assertThat(OdeFacets.childrenOf(tree(), "m49:142"))
                .extracting(OdeFacetValue::id)
                .containsExactly("iso:SG");
    }

    @Test
    void childrenOf_unknownParent_isEmpty() {
        assertThat(OdeFacets.childrenOf(tree(), "m49:999")).isEmpty();
    }

    private static List<OdeFacetValue> tree() {
        return List.of(
                OdeFacetValue.of("m49:142", "Asia"),
                OdeFacetValue.of("m49:150", "Europe"),
                new OdeFacetValue("iso:SG", "Singapore", "m49:142"),
                new OdeFacetValue("sg:central", "Central Region", "iso:SG"));
    }
}
