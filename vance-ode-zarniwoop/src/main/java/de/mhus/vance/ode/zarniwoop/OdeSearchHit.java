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

import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * One result.
 *
 * <p>{@link #title} and {@link #url} are required because the caller renders
 * every hit as a link and has nothing to show without them. A hit missing
 * either is dropped on arrival and logged — one broken row must not cost the
 * other nineteen.
 *
 * @param snippet  short excerpt for the result list. Not the body; that is
 *                 {@link #content}.
 * @param source   where this came from within the source ({@code "Reuters"},
 *                 {@code "internal wiki"}). Displayed as provenance.
 * @param modality kind of this hit. Normally the queried modality, but a source
 *                 may answer a {@code WEB} query with a {@code PDF} hit if that
 *                 is what it found.
 * @param content  the body, if this source has one to offer.
 * @param extras   anything else worth carrying (score, date, author). Shown to
 *                 the model as-is, so keep it small and self-explanatory.
 */
public record OdeSearchHit(
        String title,
        String url,
        @Nullable String snippet,
        @Nullable String source,
        OdeSearchModality modality,
        @Nullable OdeHitContent content,
        Map<String, Object> extras) {

    public OdeSearchHit {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title is required");
        }
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("url is required");
        }
        if (modality == null) {
            throw new IllegalArgumentException("modality is required");
        }
        extras = extras == null ? Map.of() : Map.copyOf(extras);
    }

    public static OdeSearchHit of(
            String title, String url, String snippet, OdeSearchModality modality) {
        return new OdeSearchHit(title, url, snippet, null, modality, null, Map.of());
    }
}
