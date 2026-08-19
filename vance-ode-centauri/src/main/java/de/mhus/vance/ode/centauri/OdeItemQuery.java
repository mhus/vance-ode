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

import java.time.Instant;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * A request for one page of one stream, as it reaches {@link FeedSource}.
 *
 * <p>{@code limit} has already been clamped to what you declared in
 * {@link OdeCapabilities#maxPageSize()}, and the filter fields are only ever
 * populated for the pushdowns you claimed — a source that declared no language
 * pushdown never sees {@link #languages()} filled. Whatever you do not
 * support, the reader filters after fetching.
 *
 * <p>{@code reader} is an opaque pseudonym, and it is optional in the strong
 * sense: <b>you must answer without it.</b> Scheduled digests and agent calls
 * have no person behind them, and a source that requires the pseudonym to
 * respond breaks them. Use it, if you want, to personalise selection or keep
 * read marks — but never to authorise, and never as an identifier of a human.
 * It is salted per source, so it is meaningless anywhere but here.
 */
public record OdeItemQuery(
        String selector,
        @Nullable String cursor,
        OdeDirection direction,
        int limit,
        @Nullable String text,
        Set<String> languages,
        @Nullable Instant since,
        @Nullable String reader) {

    public OdeItemQuery {
        selector = selector == null ? "" : selector.trim();
        direction = direction == null ? OdeDirection.OLDER : direction;
        languages = languages == null ? Set.of() : Set.copyOf(languages);
        text = blankToNull(text);
        cursor = blankToNull(cursor);
        reader = blankToNull(reader);
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be > 0, was " + limit);
        }
    }

    /** True when this is the first page of the stream. */
    public boolean isFirstPage() {
        return cursor == null;
    }

    private static @Nullable String blankToNull(@Nullable String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
