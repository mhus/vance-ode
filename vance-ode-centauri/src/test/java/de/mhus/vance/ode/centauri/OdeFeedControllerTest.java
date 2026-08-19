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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.mhus.vance.ode.inbound.OdeApiKeyInterceptor;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Here the wire contract <i>is</i> the deliverable, so these tests go through
 * the HTTP layer: paths, status codes, parameter binding and what the source
 * actually receives. A source implementer breaks on any of those, not on our
 * internal method signatures.
 */
class OdeFeedControllerTest {

    private static final String PATH = "/ode/feed";

    private RecordingSource source;
    private VanceOdeCentauriProperties properties;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        source = new RecordingSource();
        properties = new VanceOdeCentauriProperties();
        mvc = mvc(source, properties);
    }

    private static MockMvc mvc(FeedSource source, VanceOdeCentauriProperties properties) {
        var builder = MockMvcBuilders
                .standaloneSetup(new OdeFeedController(source, properties))
                .addPlaceholderValue("vance.ode.centauri.path", PATH);
        if (properties.isSecured()) {
            builder = builder.addInterceptors(new OdeApiKeyInterceptor(properties));
        }
        return builder.build();
    }

    // ── capabilities and selectors ───────────────────────────────────

    @Test
    void capabilities_areServedAsDeclared() throws Exception {
        mvc.perform(get(PATH + "/capabilities"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.selectorMode").value("ENUMERABLE"))
                .andExpect(jsonPath("$.maxPageSize").value(50))
                .andExpect(jsonPath("$.signalsAccepted[0]").value("REPORT"));
    }

    @Test
    void selectors_areServedWithLabelAndKind() throws Exception {
        mvc.perform(get(PATH + "/selectors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].value").value("world"))
                .andExpect(jsonPath("$[0].label").value("World"))
                .andExpect(jsonPath("$[0].kind").value("CATEGORY"));
    }

    // ── items ────────────────────────────────────────────────────────

    @Test
    void items_areServedWithCursorAndFlag() throws Exception {
        mvc.perform(get(PATH + "/items").param("selector", "world"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value("i1"))
                .andExpect(jsonPath("$.items[0].publishedAt").value("2026-08-19T10:00:00Z"))
                .andExpect(jsonPath("$.nextCursor").value("c-1"))
                .andExpect(jsonPath("$.hasMore").value(true));
    }

    @Test
    void items_limitIsClampedToWhatTheSourceAndOperatorAllow() throws Exception {
        properties.setMaxLimit(30);

        mvc(source, properties).perform(get(PATH + "/items").param("limit", "500"))
                .andExpect(status().isOk());

        // 500 asked, source declares 50, operator allows 30.
        assertThat(source.lastQuery().limit()).isEqualTo(30);
    }

    @Test
    void items_onlyDeclaredPushdownsReachTheSource() throws Exception {
        // The source declares text pushdown only (see RecordingSource).
        mvc.perform(get(PATH + "/items")
                        .param("text", "berlin")
                        .param("languages", "de,en")
                        .param("since", "2026-08-01T00:00:00Z"))
                .andExpect(status().isOk());

        OdeItemQuery query = source.lastQuery();
        assertThat(query.text()).isEqualTo("berlin");
        // Never handed a filter it would silently ignore — the caller applies
        // these two itself.
        assertThat(query.languages()).isEmpty();
        assertThat(query.since()).isNull();
    }

    @Test
    void items_unsupportedDirection_isRefusedBeforeReachingTheSource() throws Exception {
        mvc.perform(get(PATH + "/items").param("direction", "NEWER"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("bad_request"))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("NEWER")));

        assertThat(source.calls()).isZero();
    }

    @Test
    void items_malformedSince_isABadRequestNotASourceFailure() throws Exception {
        RecordingSource pushdownSource = new RecordingSource().withSincePushdown();

        mvc(pushdownSource, properties).perform(
                        get(PATH + "/items").param("since", "yesterday"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("ISO-8601")));
    }

    @Test
    void items_readerHeader_reachesTheSource() throws Exception {
        mvc.perform(get(PATH + "/items").header(OdeFeedHeaders.READER, "pseudo-42"))
                .andExpect(status().isOk());

        assertThat(source.lastQuery().reader()).isEqualTo("pseudo-42");
    }

    @Test
    void items_withoutReader_isStillAnswered() throws Exception {
        mvc.perform(get(PATH + "/items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value("i1"));

        // Scheduled digests have no person behind them; a source must not need
        // the pseudonym to respond.
        assertThat(source.lastQuery().reader()).isNull();
    }

    @Test
    void items_outOfOrderPage_isStillServed() throws Exception {
        RecordingSource unordered = new RecordingSource().withUnorderedPage();

        // The caller re-sorts; the violation is logged, not rejected.
        mvc(unordered, properties).perform(get(PATH + "/items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2));
    }

    // ── item body ────────────────────────────────────────────────────

    @Test
    void item_knownEntry_servesItsBody() throws Exception {
        mvc.perform(get(PATH + "/item/i1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body").value("full text of i1"));
    }

    @Test
    void item_unknownEntry_isNotFound() throws Exception {
        // An entry can age out of the stream between the page and the click.
        mvc.perform(get(PATH + "/item/gone")).andExpect(status().isNotFound());
    }

    // ── signal ───────────────────────────────────────────────────────

    @Test
    void signal_acceptedSignal_is202() throws Exception {
        mvc.perform(post(PATH + "/signal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(OdeFeedHeaders.READER, "pseudo-42")
                        .content("""
                                {"itemId":"i1","signal":"REPORT","reason":"WRONG_CATEGORY"}"""))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.outcome").value("ACCEPTED"));

        assertThat(source.lastSignal().itemId()).isEqualTo("i1");
        assertThat(source.lastSignal().reader()).isEqualTo("pseudo-42");
    }

    @Test
    void signal_undeclaredSignal_is501AndNeverReachesTheSource() throws Exception {
        // RecordingSource accepts REPORT only.
        mvc.perform(post(PATH + "/signal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"itemId":"i1","signal":"REQUEST","requestKind":"TRANSLATION"}"""))
                .andExpect(status().isNotImplemented())
                .andExpect(jsonPath("$.outcome").value("UNSUPPORTED"));

        assertThat(source.lastSignal()).isNull();
    }

    @Test
    void signal_reportWithoutReason_isRefusedWithTheContractsErrorBody() throws Exception {
        // The record validates in its constructor, so this is rejected during
        // deserialisation — before any handler. The status was always 400; the
        // body is what a caller needs to tell "you sent nonsense" from "this
        // source cannot do that", and it used to be absent.
        mvc.perform(post(PATH + "/signal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"itemId":"i1","signal":"REPORT"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("bad_request"));
    }

    @Test
    void items_unknownDirection_isRefusedWithTheContractsErrorBody() throws Exception {
        mvc.perform(get(PATH + "/items").param("direction", "SIDEWAYS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("bad_request"));
    }

    @Test
    void signal_reportWithoutReason_isRefused() throws Exception {
        mvc.perform(post(PATH + "/signal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"itemId":"i1","signal":"REPORT"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void signal_refusedByTheSource_is409() throws Exception {
        RecordingSource refusing = new RecordingSource().refusingSignals();

        mvc(refusing, properties).perform(post(PATH + "/signal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"itemId":"i1","signal":"REPORT","reason":"SPAM"}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.outcome").value("REJECTED"));
    }

    // ── shared secret ────────────────────────────────────────────────

    @Test
    void securedEndpoint_withoutToken_is401() throws Exception {
        properties.setApiKey("s3cret");

        mvc(source, properties).perform(get(PATH + "/items"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void securedEndpoint_withToken_isServed() throws Exception {
        properties.setApiKey("s3cret");

        mvc(source, properties).perform(get(PATH + "/items")
                        .header("Authorization", "Bearer s3cret"))
                .andExpect(status().isOk());
    }

    @Test
    void unsecuredEndpoint_isOpenByDesign() throws Exception {
        // Empty api-key means no check: the embedding application may already
        // guard the path, and a second scheme would fight it.
        assertThat(properties.isSecured()).isFalse();
        mvc.perform(get(PATH + "/items")).andExpect(status().isOk());
    }

    // ── fake source ──────────────────────────────────────────────────

    /** Records what the controller handed over. */
    private static final class RecordingSource implements FeedSource {

        private @Nullable OdeItemQuery lastQuery;
        private @Nullable OdeSignalRequest lastSignal;
        private int calls;
        private boolean sincePushdown;
        private boolean unordered;
        private boolean refuse;

        RecordingSource withSincePushdown() {
            this.sincePushdown = true;
            return this;
        }

        RecordingSource withUnorderedPage() {
            this.unordered = true;
            return this;
        }

        RecordingSource refusingSignals() {
            this.refuse = true;
            return this;
        }

        @Nullable OdeItemQuery lastQuery() {
            return lastQuery;
        }

        @Nullable OdeSignalRequest lastSignal() {
            return lastSignal;
        }

        int calls() {
            return calls;
        }

        @Override
        public OdeCapabilities capabilities() {
            return new OdeCapabilities(
                    OdeSelectorMode.ENUMERABLE, Set.of(OdeSelectorKind.CATEGORY),
                    /* text */ true, /* language */ false, /* since */ sincePushdown,
                    /* newer */ false, /* fullBody */ false,
                    50, Set.of(OdeSignal.REPORT), true, Duration.ofMinutes(30));
        }

        @Override
        public List<OdeSelector> selectors() {
            return List.of(OdeSelector.category("world", "World"));
        }

        @Override
        public OdeItemPage items(OdeItemQuery query) {
            this.lastQuery = query;
            this.calls++;
            OdeItem first = OdeItem.of(
                    "i1", Instant.parse("2026-08-19T10:00:00Z"), "First", "https://x.test/1");
            if (unordered) {
                OdeItem older = OdeItem.of(
                        "i0", Instant.parse("2026-08-19T08:00:00Z"), "Older", "https://x.test/0");
                return new OdeItemPage(List.of(older, first), "c-1", true);
            }
            return new OdeItemPage(List.of(first), "c-1", true);
        }

        @Override
        public Optional<OdeItemBody> body(String itemId, @Nullable String reader) {
            return "i1".equals(itemId)
                    ? Optional.of(new OdeItemBody("full text of i1"))
                    : Optional.empty();
        }

        @Override
        public OdeSignalResponse signal(OdeSignalRequest request) {
            this.lastSignal = request;
            return refuse
                    ? new OdeSignalResponse(OdeSignalOutcome.REJECTED, "not plausible")
                    : OdeSignalResponse.of(OdeSignalOutcome.ACCEPTED);
        }
    }
}
