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
package de.mhus.vance.ode.zarniwoop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.mhus.vance.ode.facet.OdeFacet;
import de.mhus.vance.ode.facet.OdeFacetValue;
import de.mhus.vance.ode.inbound.OdeAuthDecision;
import de.mhus.vance.ode.inbound.OdeAuthInterceptor;
import de.mhus.vance.ode.inbound.OdeAuthService;
import de.mhus.vance.ode.inbound.OdeCaller;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Here the wire contract <i>is</i> the deliverable, so these tests go through the
 * HTTP layer: paths, status codes, body binding and what the source actually
 * receives. A source implementer breaks on any of those, not on our internal
 * method signatures.
 */
class OdeSearchControllerTest {

    private static final String PATH = "/ode/search";

    private RecordingSearchSource source;
    private VanceOdeZarniwoopProperties properties;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        source = new RecordingSearchSource();
        properties = new VanceOdeZarniwoopProperties();
        mvc = mvc(source, properties);
    }

    private static MockMvc mvc(SearchSource source, VanceOdeZarniwoopProperties properties) {
        var builder = MockMvcBuilders
                .standaloneSetup(new OdeSearchController(source, properties))
                .addPlaceholderValue("vance.ode.zarniwoop.path", PATH);
        if (properties.isSecured()) {
            builder = builder.addInterceptors(new OdeAuthInterceptor(properties));
        }
        return builder.build();
    }

    // ── capabilities ─────────────────────────────────────────────────

    @Test
    void capabilities_areServedAsDeclared() throws Exception {
        mvc.perform(get(PATH + "/capabilities"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.modalities").isArray())
                .andExpect(jsonPath("$.maxResults").value(25))
                .andExpect(jsonPath("$.servesContent").value(false));
    }

    @Test
    void capabilities_serialiseTheTtlAsAnIso8601Duration() throws Exception {
        // Self-describing on the wire matters more in a contract between two
        // systems than brevity does — "PT30M" cannot be misread as seconds.
        mvc.perform(get(PATH + "/capabilities"))
                .andExpect(jsonPath("$.cacheTtl").value("PT30M"));
    }

    @Test
    void capabilities_defaultTheTiersRatherThanServingAnEmptySet() throws Exception {
        // An empty tier set would take the source out of every dispatch; a
        // source that says nothing means NORMAL, not "nothing".
        source.withCapabilities(new OdeSearchCapabilities(
                Set.of(OdeSearchModality.NEWS), Set.of(), Set.of(), 10, Set.of(), false, null));

        mvc.perform(get(PATH + "/capabilities"))
                .andExpect(jsonPath("$.tiers[0]").value("NORMAL"))
                .andExpect(jsonPath("$.domains[0]").value("GENERAL"));
    }

    // ── search ───────────────────────────────────────────────────────

    @Test
    void search_reachesTheSourceWithTheDeclaredQuery() throws Exception {
        source.answering(OdeSearchResponse.of(List.of(
                OdeSearchHit.of("Headline", "https://n.test/1", "teaser",
                        OdeSearchModality.NEWS))));

        mvc.perform(post(PATH + "/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"query":"tariffs","modality":"NEWS","maxResults":5}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hits[0].title").value("Headline"))
                .andExpect(jsonPath("$.hits[0].url").value("https://n.test/1"))
                .andExpect(jsonPath("$.droppedCount").value(0));

        assertThat(source.received()).singleElement().satisfies(q -> {
            assertThat(q.query()).isEqualTo("tariffs");
            assertThat(q.modality()).isEqualTo(OdeSearchModality.NEWS);
            assertThat(q.maxResults()).isEqualTo(5);
            assertThat(q.tier()).isEqualTo(OdeSearchTier.NORMAL);
        });
    }

    /**
     * What the guard decided has to arrive at the source, or authenticating is
     * only a doorman: the source cannot narrow what it serves to a caller it is
     * never told about.
     */
    @Test
    void search_carriesTheAuthenticatedCallerToTheSource() throws Exception {
        mvc.perform(post(PATH + "/search")
                        .requestAttr(OdeCaller.ATTRIBUTE, OdeCaller.of("acme"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"query":"tariffs","modality":"NEWS"}"""))
                .andExpect(status().isOk());

        assertThat(source.received()).singleElement().satisfies(q ->
                assertThat(q.caller()).isNotNull().extracting(OdeCaller::id).isEqualTo("acme"));
    }

    /** No auth service, no caller — and a source must cope with that. */
    @Test
    void search_withoutAGuard_hasNoCaller() throws Exception {
        mvc.perform(post(PATH + "/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"query":"tariffs","modality":"NEWS"}"""))
                .andExpect(status().isOk());

        assertThat(source.received()).singleElement()
                .satisfies(q -> assertThat(q.caller()).isNull());
    }

    @Test
    void search_undeclaredModality_isRefusedWithoutCallingTheSource() throws Exception {
        mvc.perform(post(PATH + "/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"query":"x","modality":"ACADEMIC"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("bad_request"))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("ACADEMIC")));

        assertThat(source.received()).isEmpty();
    }

    @Test
    void search_undeclaredTier_isRefusedWithoutCallingTheSource() throws Exception {
        mvc.perform(post(PATH + "/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"query":"x","modality":"NEWS","tier":"EXPERT"}"""))
                .andExpect(status().isBadRequest());

        assertThat(source.received()).isEmpty();
    }

    @Test
    void search_missingModality_isABadRequestNotAServerError() throws Exception {
        // The caller backs off from a 5xx, and backing off does not fix a
        // missing field.
        mvc.perform(post(PATH + "/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"query":"x"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("modality")));
    }

    @Test
    void search_blankQuery_isRefused() throws Exception {
        mvc.perform(post(PATH + "/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"query":"   ","modality":"NEWS"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("query")));

        assertThat(source.received()).isEmpty();
    }

    @Test
    void search_clampsToWhatTheSourceDeclared() throws Exception {
        mvc.perform(post(PATH + "/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"query":"x","modality":"NEWS","maxResults":999}"""))
                .andExpect(status().isOk());

        // 25 from the capabilities, below the operator's 50.
        assertThat(source.received()).singleElement()
                .satisfies(q -> assertThat(q.maxResults()).isEqualTo(25));
    }

    @Test
    void search_clampsToWhatTheOperatorAllows() throws Exception {
        // Two ceilings for two reasons: what the source can serve, and what one
        // request may cost. The lower one wins whichever it is.
        properties.setMaxResults(3);
        mvc = mvc(source, properties);

        mvc.perform(post(PATH + "/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"query":"x","modality":"NEWS","maxResults":20}"""))
                .andExpect(status().isOk());

        assertThat(source.received()).singleElement()
                .satisfies(q -> assertThat(q.maxResults()).isEqualTo(3));
    }

    @Test
    void search_absentMaxResults_getsADefaultRatherThanABodilessRejection() throws Exception {
        // The field is optional in the contract, so leaving it out has to work.
        // It only does because it is boxed: Jackson 3 fails a missing primitive
        // before any handler runs, and the caller would get a 400 with nothing
        // in it to explain a field the contract calls optional.
        mvc.perform(post(PATH + "/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"query":"x","modality":"NEWS"}"""))
                .andExpect(status().isOk());

        assertThat(source.received()).singleElement()
                .satisfies(q -> assertThat(q.maxResults())
                        .isEqualTo(OdeSearchController.DEFAULT_MAX_RESULTS));
    }

    @Test
    void search_normalTier_neverSeesExpertParams() throws Exception {
        // The promise is kept here rather than in each implementation: a source
        // that declared only NORMAL cannot act on them anyway.
        mvc.perform(post(PATH + "/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"query":"x","modality":"NEWS","expertParams":{"desk":"world"}}"""))
                .andExpect(status().isOk());

        assertThat(source.received()).singleElement()
                .satisfies(q -> assertThat(q.expertParams()).isEmpty());
    }

    @Test
    void search_expertTier_passesTheParamsThroughUntouched() throws Exception {
        source.withCapabilities(new OdeSearchCapabilities(
                Set.of(OdeSearchModality.NEWS), Set.of(OdeSearchDomain.NEWS),
                Set.of(OdeSearchTier.NORMAL, OdeSearchTier.EXPERT),
                25, Set.of("desk"), false, null));
        mvc = mvc(source, properties);

        mvc.perform(post(PATH + "/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"query":"x","modality":"NEWS","tier":"EXPERT",
                                 "expertParams":{"desk":"world","depth":3}}"""))
                .andExpect(status().isOk());

        assertThat(source.received()).singleElement()
                .satisfies(q -> assertThat(q.expertParams())
                        .containsEntry("desk", "world")
                        .containsEntry("depth", 3));
    }

    @Test
    void search_localePassesThroughAsGiven() throws Exception {
        mvc.perform(post(PATH + "/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"query":"x","modality":"NEWS","locale":"de-DE"}"""))
                .andExpect(status().isOk());

        assertThat(source.received()).singleElement()
                .satisfies(q -> assertThat(q.locale()).isEqualTo("de-DE"));
    }

    @Test
    void search_emptyResultIsAnOkAnswerNotAnError() throws Exception {
        // A 5xx would take this source out of the running for minutes, so "no
        // news today" must not be able to mean "no news tomorrow".
        source.answering(OdeSearchResponse.empty("index has nothing after 2026-01"));

        mvc.perform(post(PATH + "/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"query":"x","modality":"NEWS"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hits").isEmpty())
                .andExpect(jsonPath("$.note").value("index has nothing after 2026-01"));
    }

    @Test
    void search_droppedCountSurvivesAnEmptyHitList() throws Exception {
        // "There is something, but not for you" is a different answer from
        // "there is nothing", and the caller can only tell from this number.
        source.answering(new OdeSearchResponse(List.of(), 4, "licensed content withheld"));

        mvc.perform(post(PATH + "/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"query":"x","modality":"NEWS"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hits").isEmpty())
                .andExpect(jsonPath("$.droppedCount").value(4));
    }

    @Test
    void search_embeddedBodyTravelsWithTheHit() throws Exception {
        source.answering(OdeSearchResponse.of(List.of(new OdeSearchHit(
                "Headline", "https://n.test/1", "teaser", "Reuters",
                OdeSearchModality.NEWS,
                OdeHitContent.embedded("c1", "full text here"),
                Map.of("score", 0.8)))));

        mvc.perform(post(PATH + "/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"query":"x","modality":"NEWS"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hits[0].content.inline").value("EMBED_TEXT"))
                .andExpect(jsonPath("$.hits[0].content.text").value("full text here"))
                .andExpect(jsonPath("$.hits[0].source").value("Reuters"))
                .andExpect(jsonPath("$.hits[0].extras.score").value(0.8));
    }

    // ── content ──────────────────────────────────────────────────────

    @Test
    void content_isNotFoundWhenTheSourceDidNotDeclareIt() throws Exception {
        // Declared, not discovered: a source that says false is never consulted.
        mvc.perform(get(PATH + "/content/c1"))
                .andExpect(status().isNotFound());
    }

    @Test
    void content_servesTheBytesWithTheDeclaredMimeType() throws Exception {
        source.withCapabilities(new OdeSearchCapabilities(
                        Set.of(OdeSearchModality.PDF), Set.of(OdeSearchDomain.INTERNAL),
                        Set.of(OdeSearchTier.NORMAL), 10, Set.of(), true, null))
                .serving(new OdeContentBody("application/pdf", new byte[]{1, 2, 3}));
        mvc = mvc(source, properties);

        mvc.perform(get(PATH + "/content/c1"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"));
    }

    @Test
    void content_isNotFoundWhenTheBodyHasGoneAway() throws Exception {
        // A document can be withdrawn between the search and the fetch; that is
        // a legitimate 404, not a failure of the source.
        source.withCapabilities(new OdeSearchCapabilities(
                Set.of(OdeSearchModality.PDF), Set.of(OdeSearchDomain.INTERNAL),
                Set.of(OdeSearchTier.NORMAL), 10, Set.of(), true, null));
        mvc = mvc(source, properties);

        mvc.perform(get(PATH + "/content/gone"))
                .andExpect(status().isNotFound());
    }

    /**
     * A stash id is guessable enough that finding one is not an entitlement, so
     * the fetch is told who is asking as well.
     */
    @Test
    void content_carriesTheAuthenticatedCallerToTheSource() throws Exception {
        var licensed = new LicensedContentSource();
        mvc = mvc(licensed, properties);

        mvc.perform(get(PATH + "/content/c1")
                        .requestAttr(OdeCaller.ATTRIBUTE, OdeCaller.of("acme")))
                .andExpect(status().isOk());

        assertThat(licensed.callerId).isEqualTo("acme");
    }

    // ── the shared guard, on this path ───────────────────────────────

    @Test
    void anUnsetApiKeyLeavesTheEndpointOpen() {
        // The default, and deliberate: the embedding application may already
        // guard the path, and a second scheme would fight it.
        assertThat(properties.isSecured()).isFalse();
    }

    @Test
    void aConfiguredApiKeyRejectsAnUnauthenticatedRequest() throws Exception {
        properties.setApiKey("s3cret");
        mvc = mvc(source, properties);

        mvc.perform(get(PATH + "/capabilities"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aConfiguredApiKeyAcceptsTheBearerToken() throws Exception {
        properties.setApiKey("s3cret");
        mvc = mvc(source, properties);

        mvc.perform(get(PATH + "/capabilities")
                        .header("Authorization", "Bearer s3cret"))
                .andExpect(status().isOk());
    }

    @Test
    void aWrongBearerTokenIsRejected() throws Exception {
        properties.setApiKey("s3cret");
        mvc = mvc(source, properties);

        mvc.perform(post(PATH + "/search")
                        .header("Authorization", "Bearer wrong")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"query":"x","modality":"NEWS"}"""))
                .andExpect(status().isUnauthorized());

        assertThat(source.received()).isEmpty();
    }

    /** The whole chain: token in, decision, caller on the query the source runs. */
    @Test
    void anAuthServiceGuardsThePathAndNamesTheCaller() throws Exception {
        mvc = mvcWithAuth(source, properties);

        mvc.perform(post(PATH + "/search")
                        .header("Authorization", "Bearer t-acme")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"query":"x","modality":"NEWS"}"""))
                .andExpect(status().isOk());

        assertThat(source.received()).singleElement().satisfies(q ->
                assertThat(q.caller()).isNotNull().extracting(OdeCaller::id).isEqualTo("acme"));
    }

    /** And the endpoint is guarded although no api-key was ever configured. */
    @Test
    void anAuthServiceGuardsThePathWithoutAConfiguredApiKey() throws Exception {
        assertThat(properties.isSecured()).isFalse();
        mvc = mvcWithAuth(source, properties);

        mvc.perform(post(PATH + "/search")
                        .header("Authorization", "Bearer t-somebody-else")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"query":"x","modality":"NEWS"}"""))
                .andExpect(status().isUnauthorized());

        assertThat(source.received()).isEmpty();
    }

    private static MockMvc mvcWithAuth(
            SearchSource source, VanceOdeZarniwoopProperties properties) {
        OdeAuthService auth = (token, path) -> "t-acme".equals(token)
                ? OdeAuthDecision.allow(OdeCaller.of("acme"))
                : OdeAuthDecision.unauthenticated();
        return MockMvcBuilders
                .standaloneSetup(new OdeSearchController(source, properties))
                .addPlaceholderValue("vance.ode.zarniwoop.path", PATH)
                .addInterceptors(new OdeAuthInterceptor(properties, auth))
                .build();
    }

    /** A source that serves bodies only to the caller that licensed them. */
    private static final class LicensedContentSource implements SearchSource {

        private @Nullable String callerId;

        @Override
        public OdeSearchCapabilities capabilities() {
            return new OdeSearchCapabilities(
                    Set.of(OdeSearchModality.PDF), Set.of(OdeSearchDomain.INTERNAL),
                    Set.of(OdeSearchTier.NORMAL), 10, Set.of(), true, null);
        }

        @Override
        public OdeSearchResponse search(OdeSearchQuery query) {
            return OdeSearchResponse.of(List.of());
        }

        @Override
        public java.util.Optional<OdeContentBody> content(
                String contentId, @Nullable OdeCaller caller) {
            this.callerId = caller == null ? null : caller.id();
            return java.util.Optional.of(
                    new OdeContentBody("application/pdf", new byte[]{1, 2, 3}));
        }
    }
    // ── facets ───────────────────────────────────────────────────────

    private static OdeSearchCapabilities capsWithPlaceFacet(boolean lazy) {
        return new OdeSearchCapabilities(
                Set.of(OdeSearchModality.NEWS, OdeSearchModality.WEB),
                Set.of(OdeSearchDomain.NEWS),
                Set.of(OdeSearchTier.NORMAL),
                25, Set.of("desk"), false, java.time.Duration.ofMinutes(30),
                List.of(new OdeFacet("origin-place", "Origin", true,
                        lazy ? List.of() : List.of(
                                OdeFacetValue.of("m49:142", "Asia"),
                                new OdeFacetValue("iso:SG", "Singapore", "m49:142")),
                        lazy)));
    }

    @Test
    void facets_travelWithTheCapabilities() throws Exception {
        source.withCapabilities(capsWithPlaceFacet(false));

        mvc.perform(get(PATH + "/capabilities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.facets[0].key").value("origin-place"))
                .andExpect(jsonPath("$.facets[0].values[1].parentId").value("m49:142"));
    }

    @Test
    void search_handsADeclaredFacetToTheSource() throws Exception {
        source.withCapabilities(capsWithPlaceFacet(false));

        mvc.perform(post(PATH + "/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"query":"tariffs","modality":"NEWS",
                                 "facets":{"origin-place":["m49:142"]}}"""))
                .andExpect(status().isOk());

        assertThat(source.received().get(0).facets())
                .containsEntry("origin-place", List.of("m49:142"));
    }

    @Test
    void search_dropsAFacetThisSourceNeverDeclared() throws Exception {
        mvc.perform(post(PATH + "/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"query":"tariffs","modality":"NEWS",
                                 "facets":{"origin-place":["m49:142"]}}"""))
                .andExpect(status().isOk());

        assertThat(source.received().get(0).facets()).isEmpty();
    }

    @Test
    void facetValues_ofALazyFacetAreAskedOfTheSource() throws Exception {
        source.withCapabilities(capsWithPlaceFacet(true));

        mvc.perform(get(PATH + "/facets")
                        .param("key", "origin-place")
                        .param("parent", "m49:142"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("iso:SG"));
    }

    @Test
    void facetValues_ofAnUndeclaredKeyAreEmptyRatherThanAnError() throws Exception {
        mvc.perform(get(PATH + "/facets").param("key", "origin-place"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

}
