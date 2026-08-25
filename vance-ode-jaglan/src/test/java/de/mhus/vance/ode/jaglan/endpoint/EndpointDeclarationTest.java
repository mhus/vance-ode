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
package de.mhus.vance.ode.jaglan.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * A declaration is written once, by a developer, and everything downstream trusts
 * it — so it is refused at construction rather than at a read. These tests are
 * that boundary.
 */
class EndpointDeclarationTest {

    /**
     * Every one of these is a word the reader keeps for itself and strips before
     * the query is forwarded, so a parameter of that name would never arrive.
     * Parameterised rather than one test, because the list is the thing under
     * test — it has grown once already.
     */
    @ParameterizedTest
    @ValueSource(strings = {"path", "kind", "entry", "mode", "caption", "download", "token"})
    void param_reservedName_isRefused(String name) {
        assertThatThrownBy(() -> EndpointParam.optional(
                        name, ParamType.STRING, null, "something"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reserved");
    }

    @Test
    void param_reservedNameInAnyCase_isRefused() {
        assertThatThrownBy(() -> EndpointParam.optional(
                        "Kind", ParamType.STRING, null, "how to read it"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reserved");
    }

    @Test
    void param_requiredWithDefault_isRefused() {
        assertThatThrownBy(() -> new EndpointParam(
                        "from", ParamType.STRING, true, "yesterday", "start of window",
                        List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot both be true");
    }

    @Test
    void param_defaultOutsideItsChoices_isRefused() {
        assertThatThrownBy(() -> EndpointParam.select(
                        "dimension", List.of("topic", "source"), "language", "what to group by"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("is not one of");
    }

    @Test
    void param_choicesForAScalarType_areRefused() {
        assertThatThrownBy(() -> new EndpointParam(
                        "limit", ParamType.INTEGER, false, "20", "how many",
                        List.of("10", "20")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not declare choices");
    }

    @Test
    void param_selectWithoutChoices_isRefused() {
        assertThatThrownBy(() -> new EndpointParam(
                        "dimension", ParamType.SELECT, false, null, "what to group by",
                        List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("needs choices");
    }

    @Test
    void param_withoutDescription_isRefused() {
        assertThatThrownBy(() -> EndpointParam.optional("limit", ParamType.INTEGER, "20", " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("description");
    }

    @Test
    void spec_traversalInPath_isRefused() {
        assertThatThrownBy(() -> EndpointSpec.of(
                        "reports/../etc.yaml", "application/yaml", "T", "d"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("relative segment");
    }

    @Test
    void spec_leadingSlash_isRefused() {
        assertThatThrownBy(() -> EndpointSpec.of(
                        "/reports/x.yaml", "application/yaml", "T", "d"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not start or end with");
    }

    @Test
    void spec_mimeWithoutSubtype_isRefused() {
        assertThatThrownBy(() -> EndpointSpec.of("reports/x.yaml", "yaml", "T", "d"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("type/subtype");
    }

    @Test
    void spec_sameParameterTwice_isRefused() {
        assertThatThrownBy(() -> EndpointSpec.of("reports/x.yaml", "application/yaml", "T", "d",
                        EndpointParam.optional("limit", ParamType.INTEGER, "20", "how many"),
                        EndpointParam.optional("limit", ParamType.INTEGER, "10", "how many")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("twice");
    }

    @Test
    void spec_folders_areEveryProperPrefix() {
        EndpointSpec spec = EndpointSpec.of(
                "reports/monthly/trends.yaml", "application/yaml", "T", "d");

        assertThat(spec.folders()).containsExactly("reports", "reports/monthly");
        assertThat(spec.folder()).isEqualTo("reports/monthly");
        assertThat(spec.fileName()).isEqualTo("trends.yaml");
    }

    @Test
    void spec_atTheRoot_hasNoFolders() {
        EndpointSpec spec = EndpointSpec.of("overview.yaml", "application/yaml", "T", "d");

        assertThat(spec.folders()).isEmpty();
        assertThat(spec.folder()).isEmpty();
    }
}
