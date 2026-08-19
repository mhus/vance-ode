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

import org.jspecify.annotations.Nullable;

/**
 * The body of a hit, either carried along or addressable.
 *
 * @param contentId  identifier for {@code GET {path}/content/{contentId}}.
 *                   Required even for {@link OdeContentInline#EMBED_TEXT},
 *                   because the caller uses it to tell two bodies apart.
 * @param mimeType   what the bytes are ({@code text/plain}, {@code text/html},
 *                   {@code application/pdf}).
 * @param sizeBytes  size of the full body, so a caller can decide whether to
 *                   fetch it before paying for it. Zero when unknown.
 * @param inline     see {@link OdeContentInline}.
 * @param text       the body, required for {@link OdeContentInline#EMBED_TEXT}
 *                   and ignored otherwise.
 */
public record OdeHitContent(
        String contentId,
        String mimeType,
        long sizeBytes,
        OdeContentInline inline,
        @Nullable String text) {

    public OdeHitContent {
        if (contentId == null || contentId.isBlank()) {
            throw new IllegalArgumentException("contentId is required");
        }
        if (inline == null) {
            throw new IllegalArgumentException("inline is required");
        }
        if (mimeType == null || mimeType.isBlank()) {
            mimeType = "text/plain";
        }
    }

    /** Short body shipped with the hit — the normal case. */
    public static OdeHitContent embedded(String contentId, String text) {
        return new OdeHitContent(contentId, "text/plain",
                text == null ? 0 : text.length(), OdeContentInline.EMBED_TEXT, text);
    }
}
