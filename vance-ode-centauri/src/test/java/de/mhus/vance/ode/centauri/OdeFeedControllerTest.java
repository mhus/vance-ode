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

import de.mhus.vance.ode.facet.OdeFacet;
import de.mhus.vance.ode.facet.OdeFacetValue;
import de.mhus.vance.ode.inbound.OdeAuthDecision;
import de.mhus.vance.ode.inbound.OdeAuthInterceptor;
import de.mhus.vance.ode.inbound.OdeAuthService;
import de.mhus.vance.ode.inbound.OdeCaller;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
            builder = builder.addInterceptors(new OdeAuthInterceptor(properties));
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

    // ── the authenticated caller ─────────────────────────────────────

    /**
     * The whole chain: token in, decision, caller on the query. Without this
     * last step authenticating would only be a doorman — a source cannot narrow
     * what it serves to a caller it is never told about.
     */
    @Test
    void items_carryTheAuthenticatedCallerToTheSource() throws Exception {
        mvcWithAuth(source, properties).perform(get(PATH + "/items")
                        .header("Authorization", "Bearer t-acme")
                        .header(OdeFeedHeaders.READER, "pseudo-42"))
                .andExpect(status().isOk());

        // Two different things, and they stay two: the installation that may
        // read, and the opaque pseudonym of who is reading.
        assertThat(source.lastQuery().caller()).isNotNull();
        assertThat(source.lastQuery().caller().id()).isEqualTo("acme");
        assertThat(source.lastQuery().reader()).isEqualTo("pseudo-42");
    }

    @Test
    void items_withoutAGuard_haveNoCaller() throws Exception {
        mvc.perform(get(PATH + "/items")).andExpect(status().isOk());

        assertThat(source.lastQuery().caller()).isNull();
    }

    @Test
    void anAuthServiceGuardsThePathWithoutAConfiguredApiKey() throws Exception {
        assertThat(properties.isSecured()).isFalse();

        mvcWithAuth(source, properties).perform(get(PATH + "/items")
                        .header("Authorization", "Bearer t-somebody-else"))
                .andExpect(status().isUnauthorized());

        assertThat(source.calls()).isZero();
    }

    /**
     * The two on-demand calls take the caller as a parameter rather than
     * carrying it in a record. A source that does not care overrides neither
     * and reaches the defaults — which is what every other test here does.
     */
    @Test
    void item_andSignal_carryTheCallerToo() throws Exception {
        var licensed = new CallerRecordingSource();
        MockMvc guarded = mvcWithAuth(licensed, properties);

        guarded.perform(get(PATH + "/item/i1")
                        .header("Authorization", "Bearer t-acme"))
                .andExpect(status().isOk());
        assertThat(licensed.bodyCaller).isEqualTo("acme");

        guarded.perform(post(PATH + "/signal")
                        .header("Authorization", "Bearer t-acme")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"itemId":"i1","signal":"REPORT","reason":"SPAM"}"""))
                .andExpect(status().isAccepted());
        assertThat(licensed.signalCaller).isEqualTo("acme");
    }

    private static MockMvc mvcWithAuth(
            FeedSource source, VanceOdeCentauriProperties properties) {
        OdeAuthService auth = (token, path) -> "t-acme".equals(token)
                ? OdeAuthDecision.allow(OdeCaller.of("acme"))
                : OdeAuthDecision.unauthenticated();
        return MockMvcBuilders
                .standaloneSetup(new OdeFeedController(source, properties))
                .addPlaceholderValue("vance.ode.centauri.path", PATH)
                .addInterceptors(new OdeAuthInterceptor(properties, auth))
                .build();
    }

    // ── fake source ──────────────────────────────────────────────────

    /** Records what the controller handed over. */
    // ── facets ───────────────────────────────────────────────────────

    @Test
    void facets_travelWithTheCapabilities() throws Exception {
        mvc(source.withFacets(), properties)
                .perform(get(PATH + "/capabilities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.facets[0].key").value("origin-place"))
                .andExpect(jsonPath("$.facets[0].hierarchical").value(true))
                .andExpect(jsonPath("$.facets[0].values[1].parentId").value("m49:142"));
    }

    @Test
    void facetValues_ofAnInlineFacetComeFromTheDeclaration() throws Exception {
        // No parent means the top level — not the flat tree. Answering from the
        // declaration is an optimisation; it does not change which question was
        // asked, and a reader rendering descendants as direct children would
        // show Singapore next to Asia rather than under it.
        mvc(source.withFacets(), properties)
                .perform(get(PATH + "/facets").param("key", "origin-place"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value("m49:142"));
    }

    @Test
    void facetValues_ofAnInlineFacet_honourParent() throws Exception {
        mvc(source.withFacets(), properties)
                .perform(get(PATH + "/facets")
                        .param("key", "origin-place")
                        .param("parent", "m49:142"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value("iso:SG"));
    }

    @Test
    void facetValues_ofALazyFacetAreAskedOfTheSource() throws Exception {
        mvc(source.withLazyFacet(), properties)
                .perform(get(PATH + "/facets")
                        .param("key", "origin-place")
                        .param("parent", "m49:142"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("iso:SG"))
                .andExpect(jsonPath("$[0].parentId").value("m49:142"));
    }

    @Test
    void facetValues_ofAnUndeclaredKeyAreEmptyRatherThanAnError() throws Exception {
        mvc.perform(get(PATH + "/facets").param("key", "origin-place"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void items_splitAFacetParameterAtTheFirstColonOnly() throws Exception {
        MockMvc withFacets = mvc(source.withFacets(), properties);

        withFacets.perform(get(PATH + "/items").param("facet", "origin-place:m49:142"))
                .andExpect(status().isOk());

        assertThat(source.lastQuery()).isNotNull();
        assertThat(source.lastQuery().facets())
                .containsEntry("origin-place", List.of("m49:142"));
    }

    @Test
    void items_collectRepeatedValuesOfOneFacetAsADisjunction() throws Exception {
        MockMvc withFacets = mvc(source.withFacets(), properties);

        withFacets.perform(get(PATH + "/items")
                        .param("facet", "origin-place:m49:142")
                        .param("facet", "origin-place:iso:SG"))
                .andExpect(status().isOk());

        assertThat(source.lastQuery().facets())
                .containsEntry("origin-place", List.of("m49:142", "iso:SG"));
    }

    @Test
    void items_dropAFacetThisSourceNeverDeclared() throws Exception {
        mvc.perform(get(PATH + "/items").param("facet", "origin-topic:gaming"))
                .andExpect(status().isOk());

        assertThat(source.lastQuery().facets()).isEmpty();
    }

    private static final class RecordingSource implements FeedSource {

        private @Nullable OdeItemQuery lastQuery;
        private @Nullable OdeSignalRequest lastSignal;
        private int calls;
        private boolean sincePushdown;
        private boolean unordered;
        private boolean refuse;
        private boolean facets;
        private boolean lazyFacet;

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

        RecordingSource withFacets() {
            this.facets = true;
            return this;
        }

        RecordingSource withLazyFacet() {
            this.facets = true;
            this.lazyFacet = true;
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
                    50, Set.of(OdeSignal.REPORT), true, Duration.ofMinutes(30),
                    facets
                            ? List.of(new OdeFacet("origin-place", "Origin", true,
                                    lazyFacet ? List.of() : List.of(
                                            OdeFacetValue.of("m49:142", "Asia"),
                                            new OdeFacetValue("iso:SG", "Singapore", "m49:142")),
                                    lazyFacet))
                            : List.of());
        }

        @Override
        public List<OdeFacetValue> facetValues(String key, @Nullable String parentId) {
            return List.of(new OdeFacetValue("iso:SG", "Singapore", parentId));
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
        public Optional<OdeItem> item(String itemId, @Nullable String reader) {
            if (!"i1".equals(itemId)) {
                return Optional.empty();
            }
            // The detail is the page entry with what the listing left out —
            // here the body and one extra the teaser did not carry.
            return Optional.of(new OdeItem(
                    "i1", null, Instant.parse("2026-08-19T10:00:00Z"), "First",
                    "https://x.test/1", "teaser", "full text of i1",
                    "A. Author", "en", null, null, List.of("tag"),
                    Map.of("originPlace", "Germany")));
        }

        @Override
        public OdeSignalResponse signal(OdeSignalRequest request) {
            this.lastSignal = request;
            return refuse
                    ? new OdeSignalResponse(OdeSignalOutcome.REJECTED, "not plausible")
                    : OdeSignalResponse.of(OdeSignalOutcome.ACCEPTED);
        }
    }

    /** Overrides the caller-aware variants, which the defaults otherwise hide. */
    private static final class CallerRecordingSource implements FeedSource {

        private @Nullable String bodyCaller;
        private @Nullable String signalCaller;

        @Override
        public OdeCapabilities capabilities() {
            return new OdeCapabilities(
                    OdeSelectorMode.NONE, Set.of(),
                    /* text */ false, /* language */ false, /* since */ false,
                    /* newer */ false, /* fullBody */ false,
                    50, Set.of(OdeSignal.REPORT), true, Duration.ofMinutes(30));
        }

        @Override
        public OdeItemPage items(OdeItemQuery query) {
            return new OdeItemPage(List.of(), null, false);
        }

        @Override
        public Optional<OdeItem> item(
                String itemId, @Nullable String reader, @Nullable OdeCaller caller) {
            this.bodyCaller = caller == null ? null : caller.id();
            return Optional.of(new OdeItem(
                    itemId, null, Instant.parse("2026-08-19T10:00:00Z"), "First",
                    "https://x.test/1", null, "full text of " + itemId,
                    null, null, null, null, List.of(), Map.of()));
        }

        @Override
        public OdeSignalResponse signal(OdeSignalRequest request, @Nullable OdeCaller caller) {
            this.signalCaller = caller == null ? null : caller.id();
            return OdeSignalResponse.of(OdeSignalOutcome.ACCEPTED);
        }
    }
}
