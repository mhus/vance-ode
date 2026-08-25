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
 * Computed views over a mount — declared parameters, validated once, and a
 * description of themselves that an agent can read.
 *
 * <p><b>Additive and optional.</b> Nothing in the parent package knows this one
 * exists. A source that serves a holding — a library, a directory — implements
 * {@link de.mhus.vance.ode.jaglan.FileSource} and never comes here; a source
 * that also wants to answer questions wraps itself in
 * {@link de.mhus.vance.ode.jaglan.endpoint.EndpointFileSource} and keeps
 * everything it had.
 *
 * <p><b>What this is not.</b> Not a second dispatch mechanism.
 * {@link de.mhus.vance.ode.jaglan.FileSource#open(String,
 * de.mhus.vance.ode.jaglan.OdeQuery)} already routes a path and carries
 * parameters — it is a GET in every respect that matters. What was missing is
 * one level up: nowhere to <em>declare</em> the parameters, and therefore
 * nowhere to validate them and nothing to describe. So there are no
 * annotations, no classpath scan, no path variables and no interceptors here;
 * there is a declaration, a context, and a decorator that puts the two
 * together.
 *
 * <h2>Why the declaration is the point</h2>
 *
 * <p>A parameter that is recognised, separated off, judged inapplicable and
 * dropped looks like success at every level it passes through. On the reader's
 * side that failure was built three times in two days, and what stopped it each
 * time was not attention but a signature that could not compile without the
 * query. This is the same move on the source's side:
 * {@link de.mhus.vance.ode.jaglan.endpoint.CallContext} hands out only what the
 * {@link de.mhus.vance.ode.jaglan.endpoint.EndpointSpec} declared and refuses
 * anything else before the endpoint runs, so an undeclared parameter is a
 * refusal with a reason rather than a plausible answer to a question nobody
 * answered.
 *
 * <h2>Discovery</h2>
 *
 * <p>Parameterised views are findable through no listing — the parameter space
 * belongs to the source and is not finite, so there is nothing to enumerate.
 * That makes the declaration the only discovery channel there is, which is why
 * {@link de.mhus.vance.ode.jaglan.endpoint.EndpointFileSource} serves it as a
 * file: an agent reads {@code _api.yaml} with the ordinary document tools and
 * builds the query itself. No new wire method, no new capability field.
 *
 * <p>{@code @NullMarked} package — references are non-null by default unless
 * annotated {@code @Nullable}.
 */
@NullMarked
package de.mhus.vance.ode.jaglan.endpoint;

import org.jspecify.annotations.NullMarked;
