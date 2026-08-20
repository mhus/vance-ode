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

import de.mhus.vance.ode.facet.OdeFacet;
import de.mhus.vance.ode.facet.OdeFacetValue;
import de.mhus.vance.ode.facet.OdeFacets;
import de.mhus.vance.ode.inbound.OdeCaller;
import de.mhus.vance.ode.inbound.OdeErrorResponse;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The three endpoints Zarniwoop speaks, over one {@link SearchSource}.
 *
 * <p>Everything here is boundary work — validate, clamp, translate, hand over.
 * The source is never given a query it did not declare it could serve, which is
 * why capabilities are consulted before every call rather than trusted
 * afterwards. That check also means an implementation can be written without
 * defensive code: if a modality arrives, this source declared it.
 *
 * <p><b>Search is a POST</b> even though it reads nothing, because
 * {@code expertParams} is a structured map and squeezing it into a query string
 * would invent an encoding both sides would then have to agree on.
 */
@RestController
@RequestMapping("${vance.ode.zarniwoop.path:/ode/search}")
@RequiredArgsConstructor
@Slf4j
public class OdeSearchController {

    /**
     * What a caller gets when it names no result count. Small on purpose: a
     * caller that has not thought about the number is better served by a short
     * list than by the largest one the source and the operator would allow.
     */
    static final int DEFAULT_MAX_RESULTS = 10;

    private final SearchSource source;
    private final VanceOdeZarniwoopProperties properties;

    /**
     * What can be searched here. Reader-independent and cacheable — this is the
     * endpoint that makes the research options a property of the service rather
     * than of Vancetope.
     */
    @GetMapping("/capabilities")
    public OdeSearchCapabilities capabilities() {
        return source.capabilities();
    }

    /**
     * One level of a facet's value tree, for a facet too large to travel
     * inline with the capabilities.
     *
     * <p>{@code parent} absent means the top level. An undeclared key answers
     * empty rather than 404: a reader holding a stale capabilities response
     * should find the facet gone, not the endpoint broken.
     */
    @GetMapping("/facets")
    public List<OdeFacetValue> facetValues(
            @RequestParam String key,
            @RequestParam(required = false) @Nullable String parent) {

        OdeFacet facet = OdeFacets.find(source.capabilities().facets(), key);
        if (facet == null) {
            log.debug("Facet values requested for undeclared key '{}' — empty", key);
            return List.of();
        }
        if (!facet.lazyChildren()) {
            // Everything this facet has already travelled with the
            // capabilities; answering from there keeps one source of truth.
            return facet.values();
        }
        return source.facetValues(key, parent);
    }

    /**
     * Run one search.
     *
     * <p>Three guards run before the source sees anything: an undeclared
     * modality is refused, an undeclared tier is refused, and the result count
     * is clamped to the smaller of what the source can serve and what the
     * operator allows. Expert params are passed through untouched — which keys
     * mean something is this source's business, and it is asked to ignore rather
     * than refuse the ones it does not know.
     *
     * <p>A refusal here is a 400, not a 500: the caller is expected to back off
     * from a server error, and no amount of backing off fixes a modality this
     * source does not have.
     */
    @PostMapping("/search")
    public OdeSearchResponse search(
            @RequestBody OdeSearchRequestBody body,
            @RequestAttribute(name = OdeCaller.ATTRIBUTE, required = false)
            @Nullable OdeCaller caller) {
        OdeSearchCapabilities caps = source.capabilities();

        OdeSearchModality modality = body.modality();
        if (modality == null) {
            throw new IllegalArgumentException("modality is required");
        }
        if (!caps.modalities().contains(modality)) {
            throw new IllegalArgumentException(
                    "this source does not serve modality=" + modality
                            + "; it serves " + caps.modalities());
        }

        OdeSearchTier tier = body.tier() == null ? OdeSearchTier.NORMAL : body.tier();
        if (!caps.tiers().contains(tier)) {
            throw new IllegalArgumentException(
                    "this source does not serve tier=" + tier
                            + "; it serves " + caps.tiers());
        }

        // A NORMAL-tier source is promised never to see expert params, so the
        // promise is kept here rather than left to each implementation.
        Map<String, Object> expertParams =
                tier == OdeSearchTier.EXPERT ? body.expertParams() : Map.of();

        // Narrowed to what this source declared, for the same reason the
        // modality is checked above: a facet it never claimed is a question
        // it cannot answer. Dropped and logged rather than refused — a reader
        // may be newer than this end, and one filter it can live without
        // should not turn into a broken endpoint.
        Map<String, List<String>> facets =
                OdeFacets.restrictTo(body.facets(), OdeFacets.keysOf(caps.facets()));

        OdeSearchQuery query = new OdeSearchQuery(
                body.query(),
                modality,
                tier,
                clampMaxResults(body.maxResults(), caps),
                body.locale(),
                expertParams,
                facets,
                caller);

        OdeSearchResponse response = source.search(query);
        warnIfOverLimit(response, query);
        return response;
    }

    /**
     * The body of one hit, for sources that ship expensive full texts on
     * demand.
     *
     * <p>404 for a body this source will not produce — which covers both an
     * unknown id and one that has since been withdrawn. A source that declared
     * {@code servesContent = false} is not consulted at all; it never
     * implemented the method, and calling it would only produce a less
     * informative version of the same 404.
     */
    @GetMapping("/content/{contentId}")
    public ResponseEntity<byte[]> content(
            @PathVariable String contentId,
            @RequestAttribute(name = OdeCaller.ATTRIBUTE, required = false)
            @Nullable OdeCaller caller) {
        if (!source.capabilities().servesContent()) {
            return ResponseEntity.notFound().build();
        }
        return source.content(contentId, caller)
                .map(body -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(body.mimeType()))
                        .body(body.bytes()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * A malformed or undeclared request is the caller's problem and must not
     * read as a source failure — the caller backs off from 5xx, and a wrong
     * parameter is not something backing off would fix.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<OdeErrorResponse> onBadRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest()
                .body(new OdeErrorResponse("bad_request", String.valueOf(e.getMessage())));
    }

    /**
     * The same answer for a body that never got as far as this handler — an
     * unknown modality, a malformed number. Without it those arrive as a
     * bodiless 400, and the contract's error code is the thing that tells a
     * caller apart „you sent nonsense" from „this source cannot do that".
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<OdeErrorResponse> onUnreadableBody(
            HttpMessageNotReadableException e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return ResponseEntity.badRequest()
                .body(new OdeErrorResponse("bad_request", String.valueOf(root.getMessage())));
    }

    // ── internals ────────────────────────────────────────────────────

    /**
     * Two ceilings for two different reasons: the capability figure is what the
     * source can serve, the property is what the operator lets one request
     * cost.
     */
    private int clampMaxResults(@Nullable Integer requested, OdeSearchCapabilities caps) {
        int asked = requested == null || requested <= 0 ? DEFAULT_MAX_RESULTS : requested;
        return Math.min(asked, Math.min(caps.maxResults(), properties.getMaxResults()));
    }

    /**
     * A source returning more than it was asked for is a broken promise, but not
     * one worth failing the query over — the caller will use the first
     * {@code maxResults} anyway. Log it so the source implementer finds out.
     */
    private void warnIfOverLimit(OdeSearchResponse response, OdeSearchQuery query) {
        if (response.hits().size() > query.maxResults()) {
            log.warn("Ode search: source returned {} hits for maxResults={} — "
                            + "the caller will truncate",
                    response.hits().size(), query.maxResults());
        }
    }
}
