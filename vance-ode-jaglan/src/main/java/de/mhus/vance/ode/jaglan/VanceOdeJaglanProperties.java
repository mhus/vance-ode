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
package de.mhus.vance.ode.jaglan;

import de.mhus.vance.ode.inbound.OdeInboundEndpoint;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings of the served file endpoint, bound from {@code vance.ode.jaglan.*}.
 *
 * <p>Note what is absent: no brain URL, no tenant, no token of our own. This
 * module answers requests, it does not make them.
 *
 * <p>{@link OdeInboundEndpoint} is the slice the shared guard reads — path and
 * secret. Everything else here is this endpoint's own business.
 */
@ConfigurationProperties(prefix = "vance.ode.jaglan")
@Data
public class VanceOdeJaglanProperties implements OdeInboundEndpoint {

    /**
     * Base path of the file endpoint. The default is what Vancetope assumes,
     * so a source that changes it has to say so in the mount configuration on
     * the reader's side.
     */
    private String path = "/ode/files";

    /**
     * Shared secret expected as {@code Authorization: Bearer <key>}.
     *
     * <p><b>Empty means no check.</b> Deliberate, as in every inbound Ode
     * module: an application embedding this may already have its own security
     * in front of the path, and a library insisting on a second scheme it
     * invented would be fighting its host.
     *
     * <p>Worth a second thought here specifically, though — this endpoint
     * serves <em>file contents</em>, so an unguarded path is a file server. Set
     * it unless something else in front already decides who may read.
     */
    private String apiKey = "";

    /** Search results when the caller names no limit. */
    private int defaultSearchLimit = 25;

    /**
     * Ceiling on search results, independent of what a caller asks for.
     *
     * <p>Separate from the source's own declaration for the same reason
     * Centauri's {@code maxLimit} is: the capability figure is what the source
     * can serve, this one is what the operator will let one request cost.
     */
    private int maxSearchLimit = 200;
}
