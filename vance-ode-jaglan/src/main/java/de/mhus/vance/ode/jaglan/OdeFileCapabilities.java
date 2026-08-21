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
package de.mhus.vance.ode.jaglan;

import java.time.Duration;

import org.jspecify.annotations.Nullable;

/**
 * What your file source can do. Declared, so the reader never has to find out
 * by trying.
 *
 * <p>Three of these fields decide how the reader behaves rather than merely
 * describing you:
 *
 * <ul>
 *   <li>{@link #access()} read-only makes the editor read-only on the other
 *       side, so nobody is offered a save you would refuse.</li>
 *   <li>{@link #canSearch()} false means the reader lists your tree instead of
 *       asking you to search it. Say true only if you can answer a query — for
 *       a catalogue that is usually the cheaper path by a wide margin.</li>
 *   <li>{@link #metadataTtl()} is how long the reader may cache your listings
 *       and file metadata. This is <b>permission, not a hint</b> (see
 *       below).</li>
 * </ul>
 *
 * <h2>Caching is something you grant</h2>
 *
 * <p>{@link #metadataTtl()} governs <em>listings and metadata</em> — never your
 * content. The reader streams bytes through on every read by design and keeps
 * no copy, so there is nothing to permit there.
 *
 * <p>{@link Duration#ZERO} means "do not cache". The reader honours it as far
 * as its own design allows: it keeps a metadata row per file, and that row is
 * how the ordinary document tools can address your files at all, so a zero TTL
 * becomes the shortest window it can still work with rather than none. If your
 * licence genuinely forbids any metadata persistence, this contract is not the
 * right way to expose that content.
 *
 * @param access      what you allow
 * @param canSearch   whether {@code search} is worth calling
 * @param itemCount   how much you hold, if you know. {@code null} means
 *                    unknown, which is <b>not</b> the same as 0 — a reader
 *                    showing 0 for your mount displays "empty folder"
 * @param metadataTtl how long listings and metadata may be cached
 * @param maxBytes    largest file you will serve, {@code null} for no limit
 * @param displayName label a person sees next to the mount
 */
public record OdeFileCapabilities(
        OdeFileAccess access,
        boolean canSearch,
        @Nullable Long itemCount,
        Duration metadataTtl,
        @Nullable Long maxBytes,
        @Nullable String displayName) {

    /** Applied when no TTL is stated at all. */
    public static final Duration DEFAULT_TTL = Duration.ofMinutes(5);

    public OdeFileCapabilities {
        if (access == null) access = OdeFileAccess.READ_ONLY;
        if (itemCount != null && itemCount < 0) itemCount = null;
        if (maxBytes != null && maxBytes <= 0) maxBytes = null;
        // Negative is nonsense and falls back; zero is a statement and is
        // passed through for the reader to clamp.
        if (metadataTtl == null || metadataTtl.isNegative()) {
            metadataTtl = DEFAULT_TTL;
        }
    }

    /** The pessimistic default: readable, no search, nothing else claimed. */
    public static OdeFileCapabilities readOnly() {
        return new OdeFileCapabilities(
                OdeFileAccess.READ_ONLY, false, null, DEFAULT_TTL, null, null);
    }

    /** Readable and writable, no search. */
    public static OdeFileCapabilities readWrite() {
        return new OdeFileCapabilities(
                OdeFileAccess.READ_WRITE, false, null, DEFAULT_TTL, null, null);
    }
}
