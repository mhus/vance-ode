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
package de.mhus.vance.ode.zarniwoop;

import de.mhus.vance.ode.inbound.OdeInboundEndpoint;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings of the served search endpoint, bound from
 * {@code vance.ode.zarniwoop.*}.
 *
 * <p>Note what is absent: no brain URL, no tenant, no token of our own. This
 * module answers requests, it does not make them, so none of the outbound
 * {@code vance.ode.*} settings apply.
 *
 * <p>{@link OdeInboundEndpoint} is the slice the shared guard reads — path and
 * secret. Everything else here is this endpoint's own business.
 */
@ConfigurationProperties(prefix = "vance.ode.zarniwoop")
@Data
public class VanceOdeZarniwoopProperties implements OdeInboundEndpoint {

    /**
     * Base path of the search endpoint. The property prefix names the
     * subsystem, the path names the act — an operator configuring the reader
     * end is thinking about search, not about which of our modules serves it.
     *
     * <p>The default is what Vancetope assumes, so a source that changes it has
     * to say so in the reader's endpoint configuration.
     */
    private String path = "/ode/search";

    /**
     * Shared secret expected as {@code Authorization: Bearer <key>}.
     *
     * <p><b>Empty means no check</b>, and that is deliberate rather than an
     * oversight: an application embedding this module may already have its own
     * security in front of the path, and a library that insists on a second
     * scheme it invented would be fighting its host. Set it when this endpoint
     * would otherwise be reachable by anyone.
     */
    private String apiKey = "";

    /**
     * Ceiling on {@code maxResults}, independent of what a source declares.
     *
     * <p>Both bounds exist for different reasons: the capability figure is what
     * the source can serve, this one is what the operator is willing to let a
     * single request cost.
     */
    private int maxResults = 50;
}
