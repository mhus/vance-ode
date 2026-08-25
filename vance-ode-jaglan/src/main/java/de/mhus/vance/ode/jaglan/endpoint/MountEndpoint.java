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

/**
 * One computed path in a mount: what it accepts, and what it answers.
 *
 * <p>Deliberately not called a controller. It is addressed like one — a path,
 * query parameters, one response — but it has no routing, no method verbs and no
 * request lifecycle, and borrowing the word would promise all three.
 *
 * <p>Publish one as a bean, collect them into an
 * {@link EndpointFileSource}, and the endpoint answers at its declared path
 * inside the mount.
 *
 * <h2>What an implementation may assume</h2>
 * <ol>
 *   <li><b>The parameters fit the declaration.</b> Every value has been checked
 *       against its type and its choices, every required one is present, and
 *       nothing undeclared got through. There is nothing left to validate.</li>
 *   <li><b>A plain read is a real case.</b> Called with no parameters at all
 *       when somebody opens the path rather than querying it. Answer with your
 *       defaults; what that means is yours to decide, and it is what the reader
 *       will show for the file.</li>
 *   <li><b>It has to answer inside the read.</b> There is no job queue behind
 *       this and no status document — the caller is a document being opened,
 *       sometimes on every render for every viewer. Something that cannot be
 *       computed in about a second belongs materialised under an ordinary
 *       path.</li>
 * </ol>
 *
 * <p>Implementations must be safe to call from several threads at once, like
 * every other part of a {@link de.mhus.vance.ode.jaglan.FileSource}.
 */
public interface MountEndpoint {

    /**
     * What this endpoint is. Read on every listing and every read, so it should
     * be a constant rather than something assembled per call.
     */
    EndpointSpec spec();

    /**
     * Answer the call.
     *
     * <p>Read the parameters from {@code ctx}, produce the content, hand it back
     * with {@code ctx.reply(…)}. Returning without replying is a bug and is
     * reported as one — a source that answers a read with nothing is worse than
     * one that fails, because nothing is a valid file.
     *
     * <p>Throwing is allowed and meaningful: an
     * {@link de.mhus.vance.ode.inbound.OdeBadRequestException} becomes a refusal
     * the caller can act on, anything else a failure the reader will retry.
     */
    void handle(CallContext ctx);
}
