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

import org.jspecify.annotations.Nullable;

/**
 * One file or folder, as your source describes it.
 *
 * <p><b>Paths are yours and relative to your root.</b> No leading slash, no
 * scheme, and nothing about how the reader will address it — on the other side
 * this becomes {@code _ext/<mount>/<path>}, but that prefix is the reader's
 * business and would only be something for you to get wrong. The empty path is
 * your root, which is always a folder.
 *
 * <p><b>{@link #etag()} is the one field worth going out of your way for.</b>
 * The reader has no storage handle for your content — that is the whole point
 * of a mount — so this is its only way to answer "has this changed" without
 * downloading the bytes again. A modification timestamp combined with the size
 * is a perfectly good one; a content hash is better if you have it cheaply.
 *
 * @param path      your path, no leading slash; empty string for your root
 * @param folder    {@code true} for a folder — then size and mime are ignored
 * @param size      content length in bytes; 0 if you cannot say cheaply, and
 *                  the reader falls back to counting what it received
 * @param mimeType  your claim about the content type; {@code null} is fine,
 *                  the reader falls back to the file extension
 * @param etag      opaque change token, {@code null} if you have none
 * @param modifiedAtMs epoch millis of your last change, {@code null} if unknown
 * @param title     a display name that is nicer than the file name — for a
 *                  library that knows a book's title, this is where it goes.
 *                  {@code null} means "use the file name".
 */
public record OdeFileEntry(
        String path,
        boolean folder,
        long size,
        @Nullable String mimeType,
        @Nullable String etag,
        @Nullable Long modifiedAtMs,
        @Nullable String title) {

    public OdeFileEntry {
        if (path == null) {
            throw new IllegalArgumentException("path is required (empty means your root)");
        }
        path = path.strip();
        while (path.startsWith("/")) path = path.substring(1);
        while (path.endsWith("/")) path = path.substring(0, path.length() - 1);
        if (path.isEmpty()) {
            folder = true;
        }
        if (size < 0) size = 0;
        if (folder) {
            size = 0;
            mimeType = null;
        }
    }

    /** A file with the fields most sources can fill. */
    public static OdeFileEntry file(
            String path, long size, @Nullable String mimeType, @Nullable String etag) {
        return new OdeFileEntry(path, false, size, mimeType, etag, null, null);
    }

    /** A folder. */
    public static OdeFileEntry folder(String path) {
        return new OdeFileEntry(path, true, 0, null, null, null, null);
    }
}
