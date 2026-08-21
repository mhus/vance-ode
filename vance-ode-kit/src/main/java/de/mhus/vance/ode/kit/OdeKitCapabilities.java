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

import java.util.List;

/**
 * Which kits this application serves.
 *
 * <p>Cacheable and reader-independent by contract: the same list for everyone,
 * cheap enough to answer on a schedule. A source that needs to consult a remote
 * service to answer this is doing it in the wrong place.
 *
 * @param kits the kits on offer, possibly empty. <b>Empty is a legitimate
 *        answer</b>, not an error — „nothing configured for you" and „this
 *        service is broken" have to stay distinguishable, because a reader
 *        backs off from the second one.
 */
public record OdeKitCapabilities(List<OdeKitDeclaration> kits) {

    public OdeKitCapabilities {
        kits = kits == null ? List.of() : List.copyOf(kits);
    }

    public static OdeKitCapabilities of(OdeKitDeclaration... kits) {
        return new OdeKitCapabilities(List.of(kits));
    }
}
