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

/**
 * The one interface an application implements to serve its own kit to
 * Vancetope.
 *
 * <p>Publish an implementation as a bean and this module serves the REST
 * contract over it. Several beans are normal — one per kit — and they are
 * dispatched by {@link OdeKitDeclaration#id()}.
 *
 * <p><b>Three promises the caller relies on.</b>
 * <ol>
 *   <li><b>{@link #declare()} is cheap and does not build the kit.</b> A reader
 *       checks periodically whether anything changed; if answering that costs
 *       what an install costs, the check has to be made rare, and then changes
 *       arrive late.
 *   <li><b>{@link #build} answers in seconds.</b> It runs inside an install a
 *       person is waiting on.
 *   <li><b>The revision moves exactly when the bytes move.</b> Standing still
 *       while the content changes means the change is never picked up; moving
 *       while the content stands still means every tick refetches.
 * </ol>
 *
 * <p>And one about failure: <b>serving no kit is not an exception.</b> A source
 * that has nothing for this caller declares nothing, or is simply not published
 * as a bean. Throwing marks this application as broken and makes the reader back
 * off — right for a real fault, wrong for „nothing configured here".
 *
 * <p>Implementations must be safe to call from multiple threads. For the common
 * case of a fixed set of files see {@link StaticKitSource}.
 */
public interface KitSource {

    /**
     * What this source offers, without building it.
     *
     * <p>The declaration's {@code id} is also how {@link #build} is routed here,
     * so it must be stable across restarts and across versions of this
     * application.
     */
    OdeKitDeclaration declare();

    /**
     * Assemble the kit for one request.
     *
     * <p>The request has already been routed: {@code request.kit()} equals this
     * source's declared id. What the request says about tenant and project is
     * free to shape the result — that is the reason this is a call and not a
     * static file — but nothing obliges it to.
     *
     * <p>Placeholders are <b>not</b> substituted here. Put {@code {{ accessUrl }}}
     * in a file and list that file under {@code render:} in {@code kit.yaml};
     * the reader fills it in. Substituting here would work for exactly this
     * caller and silently produce a kit tied to it.
     *
     * <h2>Shipping a credential</h2>
     * A file at {@code settings/<key>.yaml} becomes a project setting on the
     * reader. To hand over an API key of this application, write it as an
     * encrypted type and say that the value is the credential itself:
     *
     * <pre>{@code
     * type: PASSWORD
     * encoding: plain
     * value: "sk-live-abc"
     * description: "..."
     * }</pre>
     *
     * <p><b>{@code encoding: plain} is what makes this work, and it is
     * accepted only from an Ode source.</b> Without the line the reader
     * expects {@code value} to be a vault blob and asks for a password to
     * open it — which nobody can supply, because provisioning runs with no
     * one watching. The result is not an error but an absent setting, and the
     * first symptom is an opaque 401 from whatever the kit configured. That
     * exception exists because an Ode bundle is built per request and handed
     * to an authenticated caller over TLS: there is no stored copy for a
     * vault password to protect.
     *
     * <p>Two consequences worth designing around. <b>A delivered credential
     * is written once</b>, when the project has none, and never touched
     * again — an install that reset a key somebody rotated would be an
     * outage. And <b>an unset key belongs omitted, not shipped empty</b>: an
     * empty value installs a permanent blank that no later configuration
     * corrects, because of the rule just stated.
     *
     * <p>A configuration document should <em>reference</em> the setting
     * ({@code {{secret:<key>}}}) rather than carry the value: documents are
     * readable by anyone with project READ, appear over WebDAV and travel in
     * an export. Note that a file listed under {@code render:} is run through
     * a template engine first, where {@code {{secret:…}}} is a syntax error —
     * wrap it in {@code {% verbatim %}}.
     */
    OdeKitBundle build(OdeKitBuildRequest request);
}
