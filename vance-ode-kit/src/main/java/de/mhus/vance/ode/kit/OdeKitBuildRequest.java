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
 * @param installId which installation of this kit the caller is
 *        refreshing, or null when it has never installed it. Only a
 *        previous installation can be named — the id of a new one is
 *        derived from the descriptor the caller is about to download — so
 *        an absent value means „first contact", which is the useful half
 *        of the signal.
 * @param params what the caller's operator asked this application for —
 *        „the German variant with the invoicing module". Free-form and
 *        open-ended, unlike the fields above: those say who and where and
 *        are a closed set, this one says <i>what</i>, and only you know
 *        your own options. Never null; empty when nothing was configured.
 *
 *        Keys sent without a value are dropped on arrival, so an
 *        implementation never sees a null in here.
 *
 *        <p>Two things follow. Ignore keys you do not know rather than
 *        refusing — the caller cannot know your schema, and a refusal
 *        costs the whole install. And if these change what you build,
 *        make sure your {@link OdeKitDeclaration#revision()} does not
 *        pretend otherwise: it is declared by the parameter-free
 *        capabilities call, so the caller treats a params change as its
 *        own reason to refetch and does not expect you to encode it.
 */
public record OdeKitBuildRequest(
        String kit,
        @Nullable String instance,
        String tenant,
        @Nullable String project,
        @Nullable String accessUrl,
        @Nullable String installId,
        Map<String, Object> params) {

    public OdeKitBuildRequest {
        params = params == null ? Map.of() : withoutNulls(params);
    }

    /**
     * The params, minus the entries that carry no value.
     *
     * <p>{@code {"params":{"lang":null}}} is what a {@code params:} block with
     * an empty value produces on the caller's side, and it is valid JSON.
     * {@link Map#copyOf} forbids null values, so accepting it verbatim used to
     * throw inside deserialisation and be reported as "request body is not
     * valid JSON" — sending whoever configured it to look at the wrong thing.
     * A key with no value is dropped for the same reason a key we do not
     * recognise is ignored: it says nothing, and refusing costs the install.
     */
    private static Map<String, Object> withoutNulls(Map<String, Object> raw) {
        Map<String, Object> kept = new LinkedHashMap<>(raw.size());
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                kept.put(entry.getKey(), entry.getValue());
            }
        }
        return Map.copyOf(kept);
    }

    /** One param as a string, or {@code fallback} when absent or of another type. */
    public String param(String key, String fallback) {
        Object value = params.get(key);
        return value instanceof String s && !s.isBlank() ? s : fallback;
    }
}
