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
package de.mhus.vance.ode.kit;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The files of one kit, keyed by their path inside it.
 *
 * <p>Paths are relative and use {@code /} regardless of platform; one of them
 * must be {@code kit.yaml}, which is the descriptor Vancetope reads. Bytes
 * rather than strings because a kit may carry an image or a PDF, and a text-only
 * contract would push those into base64 by hand.
 *
 * <p>Held entirely in memory on purpose. A kit is documents and tool
 * definitions — kilobytes, occasionally a megabyte — and a streaming contract
 * would cost every implementation a resource lifecycle for a size that never
 * needs one.
 *
 * <p><b>The arrays are copied on the way in.</b> {@link Map#copyOf} is shallow,
 * and a source that caches its files — {@link
 * StaticKitSource#fromClasspath(String, String)} does, because a jar does not
 * change while the process runs — would otherwise
 * hand the same mutable arrays to every bundle it ever builds. One consumer
 * writing into one of them would change the kit for every later request of that
 * process, and this record is public API in a library living inside somebody
 * else's code. Kilobytes, by its own description.
 */
public record OdeKitBundle(Map<String, byte[]> files) {

    /** Descriptor every kit must carry; Vancetope refuses a delivery without it. */
    public static final String DESCRIPTOR = "kit.yaml";

    public OdeKitBundle {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("a kit bundle needs files");
        }
        if (!files.containsKey(DESCRIPTOR)) {
            // Caught here rather than at the far end: the reader would reject
            // it too, but by then the message is "the host delivered a kit
            // without a descriptor" and the stack that could say which source
            // is long gone.
            throw new IllegalArgumentException(
                    "a kit bundle must contain " + DESCRIPTOR + "; got " + files.keySet());
        }
        Map<String, byte[]> copy = new LinkedHashMap<>(files.size());
        for (Map.Entry<String, byte[]> e : files.entrySet()) {
            String path = e.getKey();
            if (path == null || path.isBlank()) {
                throw new IllegalArgumentException("a kit bundle path must not be blank");
            }
            if (path.startsWith("/") || path.contains("..") || path.contains("\\")) {
                // The reader refuses these as well — it has to, the bytes come
                // over a network. Refusing at the source turns a security
                // check on the far side into a bug report on this one.
                throw new IllegalArgumentException(
                        "kit bundle path '" + path + "' must be relative, use / and not contain ..");
            }
            if (e.getValue() == null) {
                throw new IllegalArgumentException("kit bundle entry '" + path + "' has no content");
            }
            copy.put(path, e.getValue().clone());
        }
        files = Map.copyOf(copy);
    }
}
