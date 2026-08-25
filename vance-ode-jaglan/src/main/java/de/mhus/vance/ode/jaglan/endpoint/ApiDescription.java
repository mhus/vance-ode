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
package de.mhus.vance.ode.jaglan.endpoint;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * The whole mount, described in one document — what
 * {@link EndpointFileSource#API_PATH} serves.
 *
 * <p><b>Not OpenAPI, on purpose.</b> What is addressed here are paths inside a
 * mounted file tree, not HTTP endpoints: there is no method, no status code, no
 * body schema, and the transport belongs to the reader rather than to whoever
 * reads this. A document shaped like OpenAPI that is not OpenAPI would be the
 * more expensive misunderstanding — a tool would try to drive it.
 *
 * <p>{@link #usage()} is the part that is easy to leave out and shouldn't be.
 * The declaration says what the parameters are; it does not say that they go on
 * the path as a query string, that a plain read answers with the defaults, or
 * that a parameterised read is never listed and never cached. A caller that has
 * to infer the calling convention from a parameter list will infer it wrong.
 *
 * @param mount     the mount's display name, when it declared one
 * @param about     what this mount holds, in the source's own words
 * @param access    {@code READ_ONLY} or {@code READ_WRITE}, as declared
 * @param search    whether the mount answers a search of its own catalogue
 * @param usage     how to call these paths, in plain sentences
 * @param endpoints every computed path, with its parameters
 */
public record ApiDescription(
        @Nullable String mount,
        @Nullable String about,
        String access,
        boolean search,
        List<String> usage,
        List<ApiDescription.Endpoint> endpoints) {

    /** One computed path. */
    public record Endpoint(
            String path,
            String title,
            String mime,
            String description,
            List<ApiDescription.Param> parameters) {

        static Endpoint of(EndpointSpec spec) {
            List<Param> params = new ArrayList<>(spec.params().size());
            for (EndpointParam param : spec.params()) {
                params.add(Param.of(param));
            }
            return new Endpoint(spec.path(), spec.title(), spec.mimeType(),
                    spec.description(), params);
        }
    }

    /**
     * One parameter.
     *
     * <p>{@code type} is a string here rather than the enum, because this record
     * is the wire shape and the wire carries the borrowed vocabulary — see
     * {@link ParamType#wireName()}.
     */
    public record Param(
            String name,
            String type,
            boolean required,
            @Nullable String defaultValue,
            String description,
            List<String> choices) {

        static Param of(EndpointParam param) {
            return new Param(param.name(), param.type().wireName(), param.required(),
                    param.defaultValue(), param.description(), param.choices());
        }
    }
}
