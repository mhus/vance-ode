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

import org.jspecify.annotations.Nullable;

/**
 * Who is asking for a kit, and for where.
 *
 * <p>Every field is about <i>placement</i>, and the set is closed. It is enough
 * to assemble a kit for one project and enough to find the request again in this
 * application's log when someone reports that provisioning failed — which is
 * the reason it travels at all.
 *
 * <p>There is no field for the person who triggered it, and that is a decision
 * rather than an omission. Adding one later means adding it here, where the
 * decision is written down.
 *
 * @param kit which kit of this application is meant
 * @param instance self-declared label of the installation asking, or null when
 *        it set none. <b>Not an authorisation input</b> — it is a string the
 *        caller chose. Authorise on the credential.
 * @param tenant which tenant of that installation
 * @param project which project the kit is going into, or null when the caller
 *        is not installing into one
 * @param accessUrl the address the caller reached this application at. Useful
 *        because a service behind a reverse proxy does not reliably know its
 *        own. Note it is <b>not</b> ours to answer with: the caller substitutes
 *        the value it sent, so a different address here would go nowhere.
 */
public record OdeKitBuildRequest(
        String kit,
        @Nullable String instance,
        String tenant,
        @Nullable String project,
        @Nullable String accessUrl) {}
