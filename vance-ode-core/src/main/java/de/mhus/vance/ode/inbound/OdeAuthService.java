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
 * The application's own answer to "is this bearer token good, and whose is it?".
 *
 * <p>Publish an implementation as a bean and every Ode inbound endpoint in this
 * application authenticates through it. Without one, the static
 * {@code api-key} property stays in charge — which is enough for a source
 * serving one reader and not enough for anything else: one secret cannot be
 * rotated without downtime, cannot be revoked for one reader out of ten, and
 * says nothing about who called.
 *
 * <p><b>The service replaces the static comparison, it does not join it.</b>
 * With this bean present, {@code api-key} is not consulted at all. Two parallel
 * definitions of "valid" is the kind of thing that is impossible to remove a
 * year later, and the one that would win in an emergency is never the one you
 * remember.
 *
 * <p><b>The token is opaque and stays that way.</b> This contract does not know
 * what it is — a random string in a table, a JWT, a licence key checked against
 * a billing system. That is the point of the SPI: an application that already
 * has an answer to "who is this" uses it, instead of a library inventing a
 * second scheme and fighting its host.
 *
 * <h2>What the guard does around this</h2>
 * <ul>
 *   <li>A request with no {@code Authorization: Bearer} header is refused
 *       <b>before</b> this is called. An endpoint that wants anonymous access
 *       does not publish an {@code OdeAuthService} — it is not the job of a
 *       token validator to decide that no token is fine.
 *   <li>An exception is a refusal (401), never a pass. A validator whose
 *       database is unreachable must fail closed; the caller retries.
 *   <li>{@link OdeAuthDecision#caller()} is published on the request and reaches
 *       the source in the query it authorised, so a decision here can shape what
 *       is served rather than only whether anything is.
 * </ul>
 *
 * <p><b>Answer quickly.</b> This runs on every request, ahead of the work, on
 * paths a reader calls in a loop — cache what you look up.
 *
 * <p>Implementations must be safe to call from multiple threads.
 */
@FunctionalInterface
public interface OdeAuthService {

    /**
     * Decide about one token.
     *
     * @param presentedToken what followed {@code Bearer } in the header, trimmed
     *                       and never blank — a missing header never gets here.
     *                       Compare it in constant time if you compare it at
     *                       all.
     * @param endpointPath   the normalised base path being called, e.g.
     *                       {@code /ode/search}. An application serving both
     *                       contracts can issue tokens that are good for one of
     *                       them.
     * @return never null; use the {@link OdeAuthDecision} factories.
     */
    OdeAuthDecision authenticate(String presentedToken, String endpointPath);
}
