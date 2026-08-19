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

import java.util.Map;

/**
 * Who presented the token, as an {@link OdeAuthService} identified them.
 *
 * <p><b>An installation, not a person.</b> The token belongs to a Vancetope
 * deployment that configured this endpoint, so this names a customer, a tenant
 * or a contract — never the human whose question is being answered. That
 * distinction is the whole reason the feed contract carries a separate,
 * per-source salted reader pseudonym: knowing which system is calling is
 * unavoidable, knowing who is reading is not, and the two must not be conflated
 * into one identifier.
 *
 * <p>Reaches a source on the request it authorised — {@code OdeSearchQuery} and
 * {@code OdeItemQuery} carry it, and the on-demand fetches take it as a
 * parameter. Null there means the endpoint runs without an
 * {@link OdeAuthService}: either unguarded or on the static shared secret,
 * neither of which names anybody.
 *
 * @param id         stable identifier of the caller, in whatever vocabulary the
 *                   implementing application already uses — a customer number,
 *                   a contract id, a tenant slug. Never blank.
 * @param attributes anything else the source wants to carry from the token to
 *                   the query: a plan, a licence tier, a set of collections.
 *                   Opaque here; only the application that produced them reads
 *                   them again.
 */
public record OdeCaller(String id, Map<String, Object> attributes) {

    /**
     * Request attribute the guard publishes the caller under, and the name a
     * controller binds with {@code @RequestAttribute}.
     *
     * <p>A request attribute rather than a thread local: the value belongs to
     * one request, and Spring MVC already has a way to say so that survives
     * async dispatch and does not have to be cleaned up.
     */
    public static final String ATTRIBUTE = "de.mhus.vance.ode.inbound.caller";

    public OdeCaller {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("caller id is required");
        }
        id = id.trim();
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    public static OdeCaller of(String id) {
        return new OdeCaller(id, Map.of());
    }

    public static OdeCaller of(String id, Map<String, Object> attributes) {
        return new OdeCaller(id, attributes);
    }
}
