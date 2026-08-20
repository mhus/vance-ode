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

import de.mhus.vance.ode.inbound.OdeCaller;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * The one interface an application implements to become a Vancetope research
 * provider.
 *
 * <p>Publish an implementation as a bean and this module serves the REST
 * contract over it. Nothing else is required and nothing else is registered:
 * without the bean there is no endpoint at all.
 *
 * <p><b>Two promises the caller relies on.</b> Both are about how Zarniwoop
 * runs a search, not about taste:
 * <ol>
 *   <li><b>{@link #capabilities()} is reader-independent and cheap.</b> The
 *       caller caches it and asks every reader's question against the same
 *       answer. It must not consult a remote service per call.
 *   <li><b>{@link #search} answers in seconds, not minutes.</b> Zarniwoop runs
 *       it synchronously inside a tool call, so a slow source holds up a
 *       research turn that a person is waiting on. Return what is ready and say
 *       so in the note; a partial answer beats a late one.
 * </ol>
 *
 * <p>And one about failure: <b>an empty result is not an exception.</b> Throwing
 * makes the caller treat this source as broken and stop asking for minutes —
 * which is right for a dead index and wrong for a quiet day. Throw when the
 * search could not be run; return {@link OdeSearchResponse#empty} when it ran
 * and found nothing.
 *
 * <p>Implementations must be safe to call from multiple threads.
 */
public interface SearchSource {

    /**
     * What can be searched here. Called on every request but expected to be a
     * constant or a cheap read — the caller caches it, this is not the place to
     * reach out over a network.
     */
    OdeSearchCapabilities capabilities();

    /**
     * One level of a facet's value tree, for a facet whose taxonomy is too
     * large to travel inline (see
     * {@link de.mhus.vance.ode.facet.OdeFacet#lazyChildren()}).
     *
     * <p>{@code parentId} null means the top level. The default answers
     * nothing, which is right for every source that ships its values with its
     * capabilities — and you are only asked about a facet that said otherwise.
     */
    default List<de.mhus.vance.ode.facet.OdeFacetValue> facetValues(
            String key, @Nullable String parentId) {
        return List.of();
    }

    /**
     * Run one search.
     *
     * <p>The query has already been checked against {@link #capabilities()}:
     * the modality and tier were declared, {@code maxResults} is clamped. What
     * is <i>not</i> checked is {@code expertParams} — ignore keys you do not
     * know rather than refusing, because the caller cannot know this source's
     * schema and a refusal costs the whole query.
     */
    OdeSearchResponse search(OdeSearchQuery query);

    /**
     * Body of a hit previously returned as
     * {@link OdeContentInline#STASH_ON_DEMAND}.
     *
     * <p>{@link Optional#empty()} yields a 404, which is a legitimate answer: a
     * document can be withdrawn between the search and the fetch. The default
     * refuses everything, which is correct for the many sources that ship short
     * bodies inline — declare {@code servesContent = false} and never implement
     * this.
     */
    default Optional<OdeContentBody> content(String contentId) {
        return Optional.empty();
    }

    /**
     * The same fetch, told whose token asked for it.
     *
     * <p>This is the one the endpoint calls; the default drops the caller and
     * delegates, so a source that does not care implements
     * {@link #content(String)} and never sees this. Override it instead when a
     * body is licensed rather than public — a stash id is guessable enough that
     * "you found the id" is not an entitlement.
     */
    default Optional<OdeContentBody> content(String contentId, @Nullable OdeCaller caller) {
        return content(contentId);
    }
}
