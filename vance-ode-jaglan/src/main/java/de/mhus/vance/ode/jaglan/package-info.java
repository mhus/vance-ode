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
 * Serves this application's files to Vancetope's Jaglan mount layer.
 *
 * <p>Implement {@link de.mhus.vance.ode.jaglan.FileSource}, publish it as a
 * bean, and this module exposes the REST contract Jaglan speaks. Your files
 * then appear inside Vancetope under a project path ({@code _ext/<mount>/…})
 * and can be opened, linked and embedded with the ordinary document tools.
 * Nothing happens without that bean — a library must not start serving files
 * merely by being on the classpath.
 *
 * <p><b>Direction.</b> Inbound, like {@code vance-ode-centauri}: Vancetope
 * calls in. None of the outbound configuration applies — a file source needs no
 * brain URL and no token of its own.
 *
 * <p><b>What makes this different from the other two inbound-ish contracts.</b>
 * {@code vance-ode-zarniwoop} answers "what do you have on this topic" and
 * {@code vance-ode-centauri} "what is new"; this one answers "give me
 * <em>these</em> bytes at <em>this</em> path". The difference that matters is
 * addressability: a path is stored in a link, a document reference, a binder
 * entry, and is expected to mean the same file tomorrow. If your ids churn, you
 * are a search source, not a mount.
 *
 * <p>The second difference is mechanical: this module <b>streams</b>. Content
 * goes over the wire as bytes with no JSON envelope, because a mount exists
 * precisely so a large file needs no copy on either side.
 *
 * <p><b>Nothing is copied into Vancetope.</b> It keeps a metadata row per file
 * so its document tooling can address yours, and refetches the bytes on every
 * read. How long that metadata may be cached is yours to say —
 * {@link de.mhus.vance.ode.jaglan.OdeFileCapabilities#metadataTtl()} is
 * permission, not a hint.
 *
 * <p><b>The contract belongs here, not to any one implementation.</b> The wire
 * types in this package are independent of Vancetope's internal ones rather
 * than shared classes: two ends implementing the same shape separately is what
 * makes it a contract.
 *
 * <p>{@code @NullMarked} package — references are non-null by default unless
 * annotated {@code @Nullable}.
 */
@NullMarked
package de.mhus.vance.ode.jaglan;

import org.jspecify.annotations.NullMarked;
