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

/**
 * The bytes of one hit's body, for the on-demand path.
 *
 * <p>Bytes rather than a string because a body may be a PDF. The caller writes
 * them to the project workspace and hands the path to the model; it never
 * decodes them here.
 *
 * <p>Note this record carries an array, so its generated {@code equals} compares
 * identity rather than contents. It is a carrier on the way to a response, never
 * a key and never compared — if that ever changes, this is the line to revisit.
 *
 * @param mimeType what the bytes are.
 * @param bytes    the body.
 */
public record OdeContentBody(String mimeType, byte[] bytes) {

    public OdeContentBody {
        if (bytes == null) {
            throw new IllegalArgumentException("bytes is required");
        }
        if (mimeType == null || mimeType.isBlank()) {
            mimeType = "application/octet-stream";
        }
    }
}
