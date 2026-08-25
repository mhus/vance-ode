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
import java.util.Map;

import de.mhus.vance.ode.inbound.OdeBadRequestException;
import de.mhus.vance.ode.jaglan.OdeQuery;
import org.junit.jupiter.api.Test;

/**
 * The refusals, one per test.
 *
 * <p>Two kinds, and the difference is the whole design:
 * {@link OdeBadRequestException} for something the <i>caller</i> got wrong,
 * which becomes a refusal with a reason; {@link IllegalStateException} for
 * something the <i>endpoint</i> got wrong, which is a bug and must not present
 * as a bad request.
 */
class CallContextTest {

    private static final EndpointSpec SPEC = EndpointSpec.of(
            "reports/trends.yaml", "application/yaml", "Trends", "Counted trends",
            EndpointParam.select("dimension", List.of("topic", "source"), "topic",
                    "what to group by"),
            EndpointParam.optional("limit", ParamType.INTEGER, "20", "how many rows"),
            EndpointParam.optional("verbose", ParamType.BOOLEAN, null, "more detail"),
            EndpointParam.multiSelect("language", List.of("de", "en"), "restrict to these"),
            EndpointParam.required("at", ParamType.STRING, "the window to count"));

    private static OdeQuery query(Map<String, List<String>> parameters) {
        return new OdeQuery(parameters);
    }

    private static OdeQuery valid() {
        return query(Map.of("at", List.of("2026-08-25")));
    }

    // ── what the caller can get wrong ────────────────────────────────

    @Test
    void undeclaredParameter_isRefusedRatherThanIgnored() {
        OdeQuery given = query(Map.of("at", List.of("2026-08-25"), "colour", List.of("red")));

        assertThatThrownBy(() -> CallContext.of(SPEC, given))
                .isInstanceOf(OdeBadRequestException.class)
                .hasMessageContaining("no parameter 'colour'")
                .hasMessageContaining("dimension");
    }

    @Test
    void missingRequiredParameter_isRefusedWithItsDescription() {
        assertThatThrownBy(() -> CallContext.of(SPEC, OdeQuery.EMPTY))
                .isInstanceOf(OdeBadRequestException.class)
                .hasMessageContaining("requires the parameter 'at'")
                .hasMessageContaining("the window to count");
    }

    @Test
    void valueOutsideItsChoices_isRefused() {
        OdeQuery given = query(Map.of("at", List.of("now"), "dimension", List.of("weather")));

        assertThatThrownBy(() -> CallContext.of(SPEC, given))
                .isInstanceOf(OdeBadRequestException.class)
                .hasMessageContaining("is not one of");
    }

    @Test
    void nonNumericInteger_isRefused() {
        OdeQuery given = query(Map.of("at", List.of("now"), "limit", List.of("many")));

        assertThatThrownBy(() -> CallContext.of(SPEC, given))
                .isInstanceOf(OdeBadRequestException.class)
                .hasMessageContaining("not a whole number");
    }

    @Test
    void booleanThatIsNotSpelledOut_isRefused() {
        OdeQuery given = query(Map.of("at", List.of("now"), "verbose", List.of("1")));

        assertThatThrownBy(() -> CallContext.of(SPEC, given))
                .isInstanceOf(OdeBadRequestException.class)
                .hasMessageContaining("'true' or 'false'");
    }

    @Test
    void singleValuedParameterGivenTwice_isRefusedRatherThanHalfHonoured() {
        OdeQuery given = query(Map.of("at", List.of("now"), "limit", List.of("10", "20")));

        assertThatThrownBy(() -> CallContext.of(SPEC, given))
                .isInstanceOf(OdeBadRequestException.class)
                .hasMessageContaining("takes one value but was given 2");
    }

    @Test
    void multiSelectGivenTwice_keepsBoth() {
        OdeQuery given = query(Map.of("at", List.of("now"), "language", List.of("de", "en")));

        CallContext ctx = CallContext.of(SPEC, given);

        assertThat(ctx.values("language")).containsExactly("de", "en");
    }

    // ── reading what was sent ────────────────────────────────────────

    @Test
    void absentParameter_readsAsItsDeclaredDefault() {
        CallContext ctx = CallContext.of(SPEC, valid());

        assertThat(ctx.text("dimension")).isEqualTo("topic");
        assertThat(ctx.integer("limit")).isEqualTo(20);
    }

    @Test
    void absentParameterWithoutDefault_readsAsNull() {
        CallContext ctx = CallContext.of(SPEC, valid());

        assertThat(ctx.text("verbose")).isNull();
        assertThat(ctx.flag("verbose")).isFalse();
        assertThat(ctx.values("language")).isEmpty();
    }

    @Test
    void sentValue_winsOverTheDefault() {
        OdeQuery given = query(Map.of("at", List.of("now"), "limit", List.of("5")));

        CallContext ctx = CallContext.of(SPEC, given);

        assertThat(ctx.integer("limit", 99)).isEqualTo(5);
        assertThat(ctx.parameterised()).isTrue();
    }

    @Test
    void aPlainRead_isNotParameterised() {
        EndpointSpec optionalOnly = EndpointSpec.of("reports/x.yaml", "application/yaml", "T", "d",
                EndpointParam.optional("limit", ParamType.INTEGER, "20", "how many"));

        CallContext ctx = CallContext.of(optionalOnly);

        assertThat(ctx.parameterised()).isFalse();
        assertThat(ctx.integer("limit")).isEqualTo(20);
    }

    // ── what the endpoint can get wrong ──────────────────────────────

    @Test
    void readingAnUndeclaredParameter_isABugAndNotABadRequest() {
        CallContext ctx = CallContext.of(SPEC, valid());

        assertThatThrownBy(() -> ctx.text("colour"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not declare");
    }

    @Test
    void readingAParameterAsTheWrongType_isABug() {
        CallContext ctx = CallContext.of(SPEC, valid());

        assertThatThrownBy(() -> ctx.integer("dimension"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("declares 'dimension' as select");
    }

    @Test
    void answeringTwice_isRefused() {
        CallContext ctx = CallContext.of(SPEC, valid());
        ctx.reply("first");

        assertThatThrownBy(() -> ctx.reply("second"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("answered twice");
    }

    @Test
    void notAnsweringAtAll_isReportedRatherThanServedAsAnEmptyFile() {
        CallContext ctx = CallContext.of(SPEC, valid());

        assertThat(ctx.answered()).isFalse();
        assertThatThrownBy(ctx::answer)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("without answering");
    }

    // ── the answer ───────────────────────────────────────────────────

    @Test
    void replyYaml_writesBlockYamlWithoutADocumentMarker() {
        CallContext ctx = CallContext.of(SPEC, valid());

        ctx.replyYaml(Map.of("window", "2026-08"));

        assertThat(new String(ctx.answer(), java.nio.charset.StandardCharsets.UTF_8))
                .doesNotStartWith("---")
                .contains("window: 2026-08");
    }

    @Test
    void replyYaml_keepsAStringThatLooksLikeANumberAString() {
        CallContext ctx = CallContext.of(SPEC, valid());

        ctx.replyYaml(Map.of("code", "0031"));

        assertThat(new String(ctx.answer(), java.nio.charset.StandardCharsets.UTF_8))
                .contains("code: \"0031\"");
    }

    @Test
    void replyYaml_writesLongTextOnOneLine() {
        CallContext ctx = CallContext.of(SPEC, valid());
        String sentence = "A description long enough that a wrapping emitter would "
                + "break it across lines with a trailing backslash, which is valid "
                + "YAML and no fun to read.";

        ctx.replyYaml(Map.of("description", sentence));

        assertThat(new String(ctx.answer(), java.nio.charset.StandardCharsets.UTF_8))
                .doesNotContain("\\\n")
                .contains(sentence);
    }

    @Test
    void replyYaml_leavesEmptyValuesOut() {
        CallContext ctx = CallContext.of(SPEC, valid());

        ctx.replyYaml(new ApiDescription(null, null, "READ_ONLY", false,
                List.of("read it"), List.of()));

        String yaml = new String(ctx.answer(), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(yaml).doesNotContain("mount").doesNotContain("about")
                .doesNotContain("endpoints")
                .contains("access: READ_ONLY")
                .contains("search: false");
    }
}
