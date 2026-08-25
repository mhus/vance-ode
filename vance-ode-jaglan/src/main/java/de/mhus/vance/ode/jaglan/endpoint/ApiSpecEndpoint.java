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

import java.util.function.Supplier;

/**
 * The endpoint that describes the others.
 *
 * <p>An endpoint like any of them — same interface, same context, same
 * validation — which is what keeps it honest: a query aimed at it is refused by
 * the same code that refuses one aimed at anything else, rather than by a special
 * case that has to remember to.
 *
 * <p>The description is fetched per read rather than built once. It has to be:
 * it carries the wrapped source's declared capabilities, and those are documented
 * as evaluated per request.
 */
final class ApiSpecEndpoint implements MountEndpoint {

    private static final EndpointSpec SPEC = EndpointSpec.of(
            EndpointFileSource.API_PATH,
            "application/yaml",
            "API description",
            "Every computed path in this mount, with the parameters it accepts and "
                    + "how to call it. Read this first — parameterised views appear in "
                    + "no listing, so this document is the only place they are named.");

    private final Supplier<ApiDescription> description;

    ApiSpecEndpoint(Supplier<ApiDescription> description) {
        this.description = description;
    }

    @Override
    public EndpointSpec spec() {
        return SPEC;
    }

    @Override
    public void handle(CallContext ctx) {
        ctx.replyYaml(description.get());
    }
}
