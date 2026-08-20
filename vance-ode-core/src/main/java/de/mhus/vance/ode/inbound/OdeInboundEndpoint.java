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
package de.mhus.vance.ode.inbound;

/**
 * What the guard needs to know about an inbound endpoint: where it lives and
 * which secret it expects.
 *
 * <p>An interface rather than a shared properties class, because each subsystem
 * binds its own {@code vance.ode.<subsystem>.*} block and has settings of its
 * own besides these two. This is only the part the guard reads.
 */
public interface OdeInboundEndpoint {

    /** Base path of the endpoint, e.g. {@code /ode/feed}. */
    String getPath();

    /**
     * Shared secret expected as {@code Authorization: Bearer <key>}.
     *
     * <p><b>Empty means no check.</b> An application embedding this may already
     * guard the path with its own security, and a library that insists on a
     * second scheme it invented would be fighting its host.
     */
    String getApiKey();

    default boolean isSecured() {
        return getApiKey() != null && !getApiKey().isBlank();
    }

    /**
     * {@link #getPath()} in the one shape a path pattern can be built from:
     * exactly one leading slash, no trailing one.
     *
     * <p><b>This is a security control, not tidiness.</b> Spring's
     * {@code @RequestMapping} tolerates a configured {@code /ode/feed/} and still
     * maps {@code /ode/feed/capabilities}, but the pattern
     * {@code "/ode/feed/" + "/**"} matches nothing — so a plausible trailing-slash
     * typo would leave the endpoints mapped and the guard silent, which is a
     * failure that opens access rather than closing it. Normalising here means
     * both the mapping and the guard are derived from the same value.
     *
     * <p>An endpoint mounted at the application root normalises to the
     * <b>empty string</b>, not to {@code "/"}. Same reasoning: {@code @RequestMapping("")}
     * still maps {@code /items}, while the patterns built from {@code "/"} would be
     * {@code "/"} and {@code "//**"} — neither of which matches it. Callers building
     * patterns must treat the empty result as "everything below the root"; see
     * {@link OdeInboundSecurity}.
     */
    default String normalisedPath() {
        String path = getPath() == null ? "" : getPath().trim();
        while (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        while (path.startsWith("//")) {
            path = path.substring(1);
        }
        if (path.isEmpty() || path.equals("/")) {
            return "";
        }
        return path.startsWith("/") ? path : "/" + path;
    }
}
