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

import de.mhus.vance.ode.inbound.OdeInboundEndpoint;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings of the served kit endpoint, bound from {@code vance.ode.kit.*}.
 *
 * <p>Note what is absent: no brain URL, no tenant, no token of our own. This
 * module answers requests, it does not make them.
 */
@ConfigurationProperties(prefix = "vance.ode.kit")
@Data
public class VanceOdeKitProperties implements OdeInboundEndpoint {

    /**
     * Base path of the kit endpoints, which answer at {@code <path>/capabilities}
     * and {@code <path>/build}.
     *
     * <p><b>Why this is a fixed sub-path and not the whole endpoint address</b>,
     * unlike the sibling modules: the reader configures the <i>application</i>
     * base url, and sends it back to us as {@code accessUrl} so a kit can put it
     * where it needs a base url. If the reader's configured url already
     * contained {@code /ode/kit}, then {@code accessUrl} would be the address of
     * this endpoint rather than of the application — and a template cannot strip
     * a suffix. So: the reader knows the application root, we own the sub-path.
     *
     * <p>Changing it therefore means telling the reader too, and there is
     * usually no reason to.
     */
    private String path = "/kit";

    /**
     * Shared secret expected as {@code Authorization: Bearer <key>}.
     *
     * <p><b>Empty means no check</b>, deliberately: an application embedding
     * this module may already have its own security in front of the path, and a
     * library insisting on a second scheme it invented would be fighting its
     * host. Set it when this endpoint would otherwise be reachable by anyone —
     * and note that a kit carries tool definitions, so „reachable by anyone" is
     * a bigger deal here than for a search index.
     */
    private String apiKey = "";

    /**
     * Ceiling on the packed size of one kit, in bytes.
     *
     * <p>Guards against a source that grew a directory it did not mean to
     * serve. The reader has no matching limit — it takes what arrives — so this
     * end is where an accident gets caught.
     */
    private long maxBundleBytes = 32L * 1024 * 1024;
}
