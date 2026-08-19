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
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * One entry of a stream.
 *
 * <p>{@code publishedAt} is <b>mandatory</b>, and not out of tidiness: a
 * reader mixes your stream with others into one chronological page, and that
 * merge needs one comparable ordering key. An entry without a timestamp cannot
 * take part.
 *
 * <p>{@code id} must be stable for the same entry across requests. It is the
 * last-resort tie-break when two entries share a timestamp, and it is what
 * comes back in {@link OdeSignalRequest#itemId()} — an id that changes between
 * pages produces duplicated or skipped rows in the reader's scroll.
 *
 * <p>{@code controlUrl} is your own UI for this entry, and the escape hatch
 * for everything {@link OdeSignal} does not model. Vancetope validates it
 * before it becomes a link: https only, and the host must match the base URL
 * it knows you by.
 *
 * <p><b>{@code cursor} is what makes a mixed page resumable.</b> See below —
 * it is the one field whose absence is silently expensive.
 */
public record OdeItem(
        String id,
        /**
         * Resume token for <em>exactly this entry</em>: the value that, handed
         * back as {@link OdeItemQuery#cursor()}, yields the entry after it.
         *
         * <p><b>Fill this in if your cursor is not the plain item id.</b> A
         * reader merges your stream with others, so a page it shows is almost
         * never the page it fetched from you — it cuts in the middle of your
         * batch and has to resume from that entry, not from the end of the
         * batch. {@link OdeItemPage#nextCursor()} cannot express that cut; only
         * a per-entry token can.
         *
         * <p>Null means „my cursor is the item id", which is what the reader
         * falls back to. For a source paging by {@code (publishedAt, id)} — the
         * honest scheme, because timestamps are not unique — that fallback is
         * wrong: the reader hands back a bare id, the source cannot parse it and
         * starts from the top, and the reader's scroll repeats instead of
         * advancing. Nothing errors, which is why this is stated here rather
         * than left to be discovered.
         */
        @Nullable String cursor,
        Instant publishedAt,
        String title,
        String url,
        @Nullable String summary,
        @Nullable String body,
        @Nullable String author,
        @Nullable String language,
        @Nullable String imageUrl,
        @Nullable String controlUrl,
        List<String> tags,
        Map<String, Object> extras) {

    public OdeItem {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("item id is required");
        }
        if (publishedAt == null) {
            throw new IllegalArgumentException("item publishedAt is required (item " + id + ")");
        }
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("item url is required (item " + id + ")");
        }
        if (title == null || title.isBlank()) {
            title = url;
        }
        if (cursor != null && cursor.isBlank()) {
            cursor = null;
        }
        tags = tags == null ? List.of() : List.copyOf(tags);
        extras = extras == null ? Map.of() : Map.copyOf(extras);
    }

    /**
     * The minimum a source has to produce — for a source whose cursor <b>is</b>
     * the item id. Anything else has to set {@link #cursor()}; see its note.
     */
    public static OdeItem of(String id, Instant publishedAt, String title, String url) {
        return new OdeItem(id, null, publishedAt, title, url,
                null, null, null, null, null, null, List.of(), Map.of());
    }

    /** Copy of this entry carrying {@code value} as its resume token. */
    public OdeItem withCursor(@Nullable String value) {
        return new OdeItem(id, value, publishedAt, title, url, summary, body,
                author, language, imageUrl, controlUrl, tags, extras);
    }
}
