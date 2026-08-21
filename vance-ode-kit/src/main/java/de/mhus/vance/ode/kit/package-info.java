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
 * Serves this application's own kit to Vancetope.
 *
 * <p>Implement {@link de.mhus.vance.ode.kit.KitSource}, publish it as a bean,
 * and this module exposes the two endpoints Vancetope's kit provisioning
 * speaks. Nothing happens without that bean — a library must not start serving
 * endpoints merely by being on the classpath.
 *
 * <p><b>What a kit is.</b> A bundle of documents, tool definitions, manuals and
 * settings that teaches Vancetope how to work with this application. Written by
 * hand it would drift from the API it describes; served from here it is
 * whatever the running version says it is.
 *
 * <p><b>Two ways to serve one</b>, and they look identical from the outside:
 * hand over a directory ({@link de.mhus.vance.ode.kit.StaticKitSource}) or
 * assemble the files per request. Vancetope cannot tell which, and that is the
 * point — start with a directory and become dynamic later without either end
 * changing.
 *
 * <p><b>Placeholders are filled on the reader's side.</b> A kit declares in its
 * {@code kit.yaml} which files carry them ({@code render: [...]}) and uses
 * {@code {{ accessUrl }}}, {@code {{ tenant }}}, {@code {{ project }}},
 * {@code {{ instance }}}. The values are substituted by Vancetope, not here —
 * which is why {@code accessUrl} is sent to us rather than asked of us: a host
 * behind a reverse proxy does not reliably know its own address, and a host that
 * <i>answered</i> with an address could point the kit somewhere else.
 *
 * <p><b>What the contract deliberately does not carry:</b>
 * <ul>
 *   <li><b>No person.</b> The request names the installation, the tenant and the
 *       project — where the kit is going, so a failure can be found in this
 *       application's log. It does not name who triggered it. Leaving the field
 *       out means nobody can quietly start relying on it.
 *   <li><b>No signature.</b> The host that writes the kit is the host that
 *       delivers it, so a signature would prove nothing that the transport and
 *       the credential do not already say. Vancetope treats {@code ODE} sources
 *       as unsigned by default, deliberately.
 *   <li><b>No poll interval.</b> How often a reader checks is the reader's
 *       configuration. Two ends declaring the same number is two numbers that
 *       drift.
 * </ul>
 */
@org.jspecify.annotations.NullMarked
package de.mhus.vance.ode.kit;
