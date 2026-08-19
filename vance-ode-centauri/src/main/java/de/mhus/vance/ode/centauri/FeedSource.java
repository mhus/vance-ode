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

import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * The one interface a foreign application implements to become a Vancetope
 * feed source. Publish it as a bean and this module serves the REST contract.
 *
 * <h2>Three assurances the contract rests on</h2>
 * <ol>
 *   <li><b>Pages come back chronologically</b> — see {@link OdeItemPage}.
 *       Personalise which entries appear, never their order: the reader merges
 *       your page with other sources on the timestamp, and a per-reader
 *       ranking produces a quietly wrong sequence rather than a visible error.
 *   <li><b>{@link #items} answers without a reader pseudonym.</b> A
 *       reader-specific view is an improvement, never a precondition —
 *       scheduled digests have no person behind them.
 *   <li><b>{@link #capabilities()} and {@link #selectors()} are
 *       reader-independent.</b> They describe the source, so they are cached
 *       across all readers and are never given a pseudonym.
 * </ol>
 *
 * <p>Implementations must be safe to call from several threads at once: a
 * reader fetches every stream of a page concurrently.
 */
public interface FeedSource {

    /**
     * What this source can do. Called at most once per
     * {@link OdeCapabilities#capabilitiesTtl()} and cached across readers, so
     * it may be computed rather than constant — but it must not depend on who
     * is asking.
     */
    OdeCapabilities capabilities();

    /**
     * The finite selector list of an {@link OdeSelectorMode#ENUMERABLE}
     * source. Return empty for {@code FREEFORM} and {@code NONE}; the default
     * covers both.
     */
    default List<OdeSelector> selectors() {
        return List.of();
    }

    /**
     * Reject an unusable free-text selector, returning a sentence a person can
     * act on. Only consulted for {@link OdeSelectorMode#FREEFORM} sources, and
     * worth implementing there: without it somebody types a tag with a
     * trailing space, gets an empty stream and no explanation.
     */
    default Optional<String> validateSelector(String selector) {
        return Optional.empty();
    }

    /**
     * One page of one stream. The limit is already clamped and the filters are
     * only those you declared you can apply.
     *
     * <p>Throw to signal a real failure — the caller classifies it and may
     * back off for a while. An empty page is not a failure.
     *
     * <p><b>Two cursors, and both are needed.</b>
     * {@link OdeItemPage#nextCursor()} resumes after the batch you returned;
     * {@link OdeItem#cursor()} resumes after one entry. The reader merges your
     * stream with others and therefore usually cuts your batch in the middle, so
     * the per-entry token is the one it reaches for most. Leave it null only if
     * your cursor really is the bare item id.
     */
    OdeItemPage items(OdeItemQuery query);

    /**
     * The full text of one entry. Only asked when
     * {@link OdeCapabilities#carriesFullBody()} is false; return
     * {@link Optional#empty()} for an entry you do not know, which becomes a
     * 404.
     */
    default Optional<OdeItemBody> body(String itemId, @Nullable String reader) {
        return Optional.empty();
    }

    /**
     * Take a back-channel signal. Only called for signals you listed in
     * {@link OdeCapabilities#signalsAccepted()}, so the default refusal is
     * reached only if the two disagree.
     *
     * <p>Fire-and-forget: answer that you received it, not what you will do.
     * Anything you want to report back to a person belongs in your own UI, to
     * which {@link OdeItem#controlUrl()} is the door.
     */
    default OdeSignalResponse signal(OdeSignalRequest request) {
        return OdeSignalResponse.of(OdeSignalOutcome.UNSUPPORTED);
    }
}
