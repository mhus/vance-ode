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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

/**
 * Content hash of a kit's files — path-sorted, so it does not depend on the
 * order they were collected in.
 *
 * <p>Deliberately not a hash of the archive: a zip carries timestamps and entry
 * order, so two identical kits packed twice would differ and every check would
 * report a change. Deliberately not a hash of a version string either — that is
 * the thing a revision has to be independent of.
 *
 * <p>Same construction Vancetope uses for a signed kit tree, but this is not a
 * signature and proves nothing about origin. It answers one question: did the
 * content change.
 */
public final class KitTreeHash {

    private KitTreeHash() {
    }

    /** Hash of a file map, as lowercase hex. */
    public static String of(Map<String, byte[]> files) {
        MessageDigest total = sha256();
        // Sorted so collection order cannot leak into the result. A caller
        // handing us a HashMap must get the same answer as one handing us a
        // list built by walking a directory.
        for (Map.Entry<String, byte[]> e : new TreeMap<>(files).entrySet()) {
            total.update(e.getKey().getBytes(StandardCharsets.UTF_8));
            total.update((byte) 0);
            total.update(sha256().digest(e.getValue()));
        }
        return HexFormat.of().formatHex(total.digest());
    }

    /** Files of a directory tree, keyed by their {@code /}-separated relative path. */
    public static Map<String, byte[]> read(Path root) {
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("not a directory: " + root);
        }
        Map<String, byte[]> files = new TreeMap<>();
        try (var walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile).forEach(file -> {
                String relative = root.relativize(file).toString().replace('\\', '/');
                try {
                    files.put(relative, Files.readAllBytes(file));
                } catch (IOException e) {
                    throw new UncheckedIOException("failed to read " + file, e);
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read the kit directory " + root, e);
        }
        return files;
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required to be present", e);
        }
    }
}
