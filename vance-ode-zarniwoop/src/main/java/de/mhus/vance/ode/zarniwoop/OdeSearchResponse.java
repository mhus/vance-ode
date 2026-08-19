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

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * What came back.
 *
 * <p><b>An empty result is not a failure.</b> {@code hits: []} with a
 * {@link #note} is the correct answer to „nothing found"; a 5xx is not, and the
 * difference matters more than it looks: the caller treats a server error as the
 * source being broken and takes it out of the running for minutes. „No news
 * today" would then also mean „no news tomorrow".
 *
 * <p>There is no returned-count field. The caller counts {@link #hits}, and a
 * number that can disagree with the list beside it is a bug waiting to be
 * reported as a mystery.
 *
 * @param hits         results, in the order the source wants them shown. The
 *                     caller does not re-rank; ordering is the source's
 *                     statement about its own results.
 * @param droppedCount how many results were withheld — by a filter, a licence,
 *                     a permission. Nonzero with an empty list is meaningful
 *                     („there is something, but not for you") and worth saying.
 * @param note         one sentence for the model or the log: why the list is
 *                     short, what was interpreted, what was ignored.
 */
public record OdeSearchResponse(
        List<OdeSearchHit> hits,
        int droppedCount,
        @Nullable String note) {

    public OdeSearchResponse {
        hits = hits == null ? List.of() : List.copyOf(hits);
        droppedCount = Math.max(0, droppedCount);
    }

    public static OdeSearchResponse of(List<OdeSearchHit> hits) {
        return new OdeSearchResponse(hits, 0, null);
    }

    /** Nothing found, and why. Not an error. */
    public static OdeSearchResponse empty(String note) {
        return new OdeSearchResponse(List.of(), 0, note);
    }
}
