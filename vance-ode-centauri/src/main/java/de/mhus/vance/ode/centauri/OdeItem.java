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
 */
public record OdeItem(
        String id,
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
        tags = tags == null ? List.of() : List.copyOf(tags);
        extras = extras == null ? Map.of() : Map.copyOf(extras);
    }

    /** The minimum a source has to produce. */
    public static OdeItem of(String id, Instant publishedAt, String title, String url) {
        return new OdeItem(id, publishedAt, title, url,
                null, null, null, null, null, null, List.of(), Map.of());
    }
}
