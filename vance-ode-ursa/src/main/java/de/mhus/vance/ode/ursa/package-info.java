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
/**
 * Firing Vancetope events from outside.
 *
 * <p>The event endpoint is the least demanding way into a brain: a plain
 * HTTP POST with a bearer token, no JWT, no service account, no token
 * refresh. What the event does — spawn a worker, run a script, call a
 * model — is configured on the brain side and invisible here, which is
 * the property that makes this the right integration surface: the caller
 * names a capability, not an implementation.
 *
 * <p>A synchronous event returns its result in the same response; see
 * {@link de.mhus.vance.ode.ursa.EventResult}.
 */
@NullMarked
package de.mhus.vance.ode.ursa;

import org.jspecify.annotations.NullMarked;
