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

import java.time.Duration;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * What can be searched here — the answer to „what are the research options of
 * this service", and the reason this module exists rather than a hardcoded
 * provider in Vancetope.
 *
 * <p><b>Reader-independent and cacheable.</b> Nothing in here may depend on who
 * is asking; the caller holds it for {@link #cacheTtl} and hands the same answer
 * to every reader. A source whose capabilities depend on the caller has to
 * express that by answering fewer queries, not by varying this.
 *
 * @param modalities    kinds of result this source can serve. Empty means the
 *                      caller has nothing to dispatch to it — a source that
 *                      declares nothing will never be asked anything.
 * @param domains       subject-area hints for provider selection; never null.
 * @param tiers         tiers this source can serve. Declaring only
 *                      {@link OdeSearchTier#NORMAL} is the honest answer for
 *                      anything that cannot act on expert params.
 * @param maxResults    largest result count this source will serve. The caller
 *                      clamps to it, so an implementation need not defend
 *                      against a larger request.
 * @param expertParams  names of the expert params this source understands.
 *                      <b>Informational only</b> — the caller passes through
 *                      whatever it was given and shows this list to an operator.
 *                      There is no structured surface for it in Vancetope yet, so
 *                      the field documents rather than validates.
 * @param servesContent whether {@code GET {path}/content/{contentId}} works. A
 *                      declaration rather than a discovery: a caller that gets a
 *                      {@link OdeContentInline#STASH_ON_DEMAND} hit from a source
 *                      that says {@code false} knows it is looking at a bug and
 *                      can say so, instead of finding out with a 404 later.
 * @param cacheTtl      how long this answer may be held, as an ISO-8601 duration
 *                      ({@code PT30M}). Null lets the caller pick.
 */
public record OdeSearchCapabilities(
        Set<OdeSearchModality> modalities,
        Set<OdeSearchDomain> domains,
        Set<OdeSearchTier> tiers,
        int maxResults,
        Set<String> expertParams,
        boolean servesContent,
        @Nullable Duration cacheTtl) {

    public OdeSearchCapabilities {
        modalities = modalities == null ? Set.of() : Set.copyOf(modalities);
        // Empty and absent mean the same thing for these two, and neither is a
        // usable answer: an empty tier set takes the source out of every
        // dispatch, and an empty domain set makes it invisible to the research
        // plan. A source that says nothing means the default, not "nothing".
        // Modalities are different — an empty set there is a real statement
        // ("ask me nothing") and is left alone.
        domains = domains == null || domains.isEmpty()
                ? Set.of(OdeSearchDomain.GENERAL)
                : Set.copyOf(domains);
        tiers = tiers == null || tiers.isEmpty()
                ? Set.of(OdeSearchTier.NORMAL)
                : Set.copyOf(tiers);
        expertParams = expertParams == null ? Set.of() : Set.copyOf(expertParams);
        if (maxResults <= 0) {
            maxResults = 20;
        }
    }

    /**
     * The common case: one modality, general subject area, normal tier only, no
     * content endpoint.
     */
    public static OdeSearchCapabilities of(OdeSearchModality modality, int maxResults) {
        return new OdeSearchCapabilities(
                Set.of(modality), Set.of(OdeSearchDomain.GENERAL),
                Set.of(OdeSearchTier.NORMAL), maxResults, Set.of(), false, null);
    }
}
