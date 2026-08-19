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
}
