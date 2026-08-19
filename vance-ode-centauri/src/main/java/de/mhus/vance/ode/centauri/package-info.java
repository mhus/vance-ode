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
 * Serves this application's time-ordered streams to Vancetope's Centauri
 * feed reader.
 *
 * <p>Implement {@link de.mhus.vance.ode.centauri.FeedSource}, publish it as a
 * bean, and this module exposes the REST contract Centauri speaks. Nothing
 * happens without that bean — a library must not start serving endpoints
 * merely by being on the classpath.
 *
 * <p><b>Direction.</b> This is the first inbound module: Vancetope calls in.
 * The outbound modules ({@code vance-ode-core}, {@code vance-ode-ursa}) are
 * about reaching a brain, and none of their configuration applies here — a
 * feed source needs no brain URL and no token of its own.
 *
 * <p><b>The contract belongs here, not to any one implementation.</b>
 * Hrafnagud is the first source to speak it, not its measure. That is also
 * why the wire types in this package are independent of Vancetope's internal
 * ones rather than shared classes: two ends implementing the same shape
 * separately is what makes it a contract.
 *
 * <p>{@code @NullMarked} package — references are non-null by default unless
 * annotated {@code @Nullable}.
 */
@NullMarked
package de.mhus.vance.ode.centauri;

import org.jspecify.annotations.NullMarked;
