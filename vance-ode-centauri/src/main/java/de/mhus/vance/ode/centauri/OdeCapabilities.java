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

import de.mhus.vance.ode.facet.OdeFacet;
import java.time.Duration;
import java.util.List;
import java.util.Set;

/**
 * What this source can do. Declared, so Vancetope never has to find out by
 * trying.
 *
 * <p>Two things hang off it, and both protect you rather than Vancetope:
 * <ul>
 *   <li>Every {@code pushdown*} you leave false is a filter Vancetope applies
 *       itself after fetching. You are never handed a filter you did not claim
 *       to understand.
 *   <li>An empty {@link #signalsAccepted()} makes the reader's UI hide those
 *       buttons instead of offering one that fails against you.
 * </ul>
 *
 * <p>The honest default is the pessimistic one: {@link #readOnly} claims
 * nothing beyond serving a taxonomy and pages.
 */
public record OdeCapabilities(
        OdeSelectorMode selectorMode,
        Set<OdeSelectorKind> selectorKinds,
        boolean pushdownTextSearch,
        boolean pushdownLanguage,
        boolean pushdownSince,
        boolean supportsNewerDirection,
        boolean carriesFullBody,
        int maxPageSize,
        Set<OdeSignal> signalsAccepted,
        boolean carriesControlUrl,
        Duration capabilitiesTtl,
        /**
         * Dimensions this source can be filtered by — see
         * {@link OdeFacet}. Empty is the normal answer.
         *
         * <p>Unlike the {@code pushdown*} flags above this one has no
         * fallback: what you decline here is not filtered elsewhere, it
         * simply takes you out of a request that asked for it.
         */
        List<OdeFacet> facets,
        /**
         * Which {@link OdeItem#extras()} keys a reader should show, in the
         * order it should show them — see {@link OdeExtraField}. Empty means
         * none, which is the honest answer for a source whose extras are
         * machine-facing.
         */
        List<OdeExtraField> extraFields) {

    public static final Duration DEFAULT_TTL = Duration.ofMinutes(30);

    public static final int DEFAULT_MAX_PAGE_SIZE = 40;

    /** The same declaration without facets — the shape that predates them. */
    public OdeCapabilities(
            OdeSelectorMode selectorMode,
            Set<OdeSelectorKind> selectorKinds,
            boolean pushdownTextSearch,
            boolean pushdownLanguage,
            boolean pushdownSince,
            boolean supportsNewerDirection,
            boolean carriesFullBody,
            int maxPageSize,
            Set<OdeSignal> signalsAccepted,
            boolean carriesControlUrl,
            Duration capabilitiesTtl) {
        this(selectorMode, selectorKinds, pushdownTextSearch, pushdownLanguage,
                pushdownSince, supportsNewerDirection, carriesFullBody, maxPageSize,
                signalsAccepted, carriesControlUrl, capabilitiesTtl, List.of(), List.of());
    }

    /** Capabilities with facets but no declared extras. */
    public OdeCapabilities(
            OdeSelectorMode selectorMode,
            Set<OdeSelectorKind> selectorKinds,
            boolean pushdownTextSearch,
            boolean pushdownLanguage,
            boolean pushdownSince,
            boolean supportsNewerDirection,
            boolean carriesFullBody,
            int maxPageSize,
            Set<OdeSignal> signalsAccepted,
            boolean carriesControlUrl,
            Duration capabilitiesTtl,
            List<OdeFacet> facets) {
        this(selectorMode, selectorKinds, pushdownTextSearch, pushdownLanguage,
                pushdownSince, supportsNewerDirection, carriesFullBody, maxPageSize,
                signalsAccepted, carriesControlUrl, capabilitiesTtl, facets, List.of());
    }

    public OdeCapabilities {
        if (selectorMode == null) {
            selectorMode = OdeSelectorMode.NONE;
        }
        selectorKinds = selectorKinds == null ? Set.of() : Set.copyOf(selectorKinds);
        signalsAccepted = signalsAccepted == null ? Set.of() : Set.copyOf(signalsAccepted);
        facets = facets == null ? List.of() : List.copyOf(facets);
        extraFields = extraFields == null ? List.of() : List.copyOf(extraFields);
        if (maxPageSize <= 0) {
            maxPageSize = DEFAULT_MAX_PAGE_SIZE;
        }
        if (capabilitiesTtl == null || capabilitiesTtl.isNegative() || capabilitiesTtl.isZero()) {
            capabilitiesTtl = DEFAULT_TTL;
        }
        if (selectorMode == OdeSelectorMode.FREEFORM && selectorKinds.isEmpty()) {
            throw new IllegalArgumentException(
                    "a FREEFORM source must declare at least one selectorKind — "
                            + "otherwise the reader's configuration form has no field to render");
        }
    }

    /** A source with a taxonomy, no pushdown and no back channel. */
    public static OdeCapabilities readOnly(int maxPageSize) {
        return new OdeCapabilities(
                OdeSelectorMode.ENUMERABLE, Set.of(OdeSelectorKind.CATEGORY),
                false, false, false, false, false,
                maxPageSize, Set.of(), false, DEFAULT_TTL);
    }
}
