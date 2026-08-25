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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.Nullable;

/**
 * What one endpoint is: where it lives, what it answers with, and every
 * parameter it accepts.
 *
 * <p><b>The parameter list is exhaustive, and that is what makes it useful.</b>
 * {@link CallContext} refuses anything not declared here, so this is not
 * documentation that can drift from the implementation — it is the implementation
 * of what the endpoint accepts.
 *
 * <h2>One path, one type</h2>
 *
 * <p>{@link #mimeType()} belongs to the path and not to a request, because the
 * reader renders from the mime on its own metadata row: a response claiming a
 * different type would not survive the trip. An endpoint that needs to answer in
 * two formats is two endpoints — {@code report.yaml} beside {@code report.md} —
 * and not one with a {@code format} parameter that changes what it returns.
 *
 * <p>Which is what {@link #renderingOf()} is for. The second of those two
 * endpoints says whose answer it re-renders, and the served description then
 * carries <b>one</b> entry for the report with its formats listed, instead of the
 * same six parameters twice. Without it, two formats of one report read as two
 * unrelated endpoints that happen to look alike.
 *
 * <p>There are no path variables. Exact paths only, and the omission is
 * deliberate: a variable in the path is where this would stop being a
 * declaration and start being a router, and a mount that needs one address per
 * instance already has a real tree to put them in.
 *
 * @param path        where the endpoint answers, relative to the mount root, no
 *                    leading slash. Anywhere in your namespace: this module
 *                    reserves no folder and suggests none — the only path it
 *                    claims for itself is
 *                    {@link EndpointFileSource#API_PATH}. Worth keeping clear of
 *                    the holding you serve, though, since an endpoint shadows a
 *                    wrapped path of the same name
 * @param mimeType    the type of every answer from this path
 * @param title       the display name the reader shows for the file
 * @param description what this endpoint answers. One or two sentences — an agent
 *                    reads this to decide whether to call it at all
 * @param params      every parameter it accepts, in the order they should be
 *                    presented. May be empty: an endpoint without parameters is
 *                    an ordinary file that happens to be computed
 * @param renderingOf the path of the endpoint whose answer this one renders
 *                    differently, or {@code null} when it stands on its own. Set
 *                    it and this endpoint stops having its own entry in the
 *                    served description and becomes a format under that one —
 *                    still listed in the tree, still readable, still parameterised
 *                    in exactly the same way. That last part is enforced: the
 *                    parameters have to match the endpoint pointed at, because a
 *                    format described under someone else's parameter list while
 *                    accepting a different one is a documented lie
 */
public record EndpointSpec(
        String path,
        String mimeType,
        String title,
        String description,
        List<EndpointParam> params,
        @Nullable String renderingOf) {

    public EndpointSpec {
        path = path == null ? "" : path.strip();
        if (path.isEmpty()) {
            throw new IllegalArgumentException("an endpoint needs a path");
        }
        if (path.startsWith("/") || path.endsWith("/")) {
            throw new IllegalArgumentException(
                    "endpoint path must not start or end with '/': '" + path + "'");
        }
        if (path.indexOf('\\') >= 0 || path.indexOf('\0') >= 0 || path.indexOf('?') >= 0) {
            throw new IllegalArgumentException(
                    "endpoint path must not contain '\\', '?' or NUL: '" + path + "'");
        }
        for (String segment : path.split("/")) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
                throw new IllegalArgumentException(
                        "endpoint path has an empty or relative segment: '" + path + "'");
            }
        }
        if (mimeType == null || mimeType.isBlank() || mimeType.indexOf('/') < 0) {
            throw new IllegalArgumentException(
                    "endpoint '" + path + "' needs a mime type of the form type/subtype");
        }
        mimeType = mimeType.strip();
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("endpoint '" + path + "' needs a title");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("endpoint '" + path + "' needs a description, "
                    + "which is what a caller reads to know whether to call it");
        }
        params = params == null ? List.of() : List.copyOf(params);
        Set<String> seen = new LinkedHashSet<>();
        for (EndpointParam param : params) {
            if (!seen.add(param.name())) {
                throw new IllegalArgumentException("endpoint '" + path
                        + "' declares the parameter '" + param.name() + "' twice");
            }
        }
        renderingOf = renderingOf == null || renderingOf.isBlank() ? null : renderingOf.strip();
        if (path.equals(renderingOf)) {
            throw new IllegalArgumentException(
                    "endpoint '" + path + "' cannot be a rendering of itself");
        }
    }

    /** A parameterless endpoint — a computed file. */
    public static EndpointSpec of(
            String path, String mimeType, String title, String description) {
        return new EndpointSpec(path, mimeType, title, description, List.of(), null);
    }

    /** The same with parameters. */
    public static EndpointSpec of(String path, String mimeType, String title,
            String description, EndpointParam... params) {
        return new EndpointSpec(path, mimeType, title, description, List.of(params), null);
    }

    /**
     * The same spec, declared as another rendering of {@code path}.
     *
     * <p>A wither rather than a sixth argument on the factories: this is the
     * unusual case, and every call site that does not use it should not have to
     * pass a null for it.
     */
    public EndpointSpec asRenderingOf(String path) {
        return new EndpointSpec(
                this.path, mimeType, title, description, params, path);
    }

    /** Whether this endpoint has an entry of its own in the description. */
    public boolean standsAlone() {
        return renderingOf == null;
    }

    /** The declaration of {@code name}, or empty when it was not declared. */
    public Optional<EndpointParam> param(String name) {
        for (EndpointParam param : params) {
            if (param.name().equals(name)) {
                return Optional.of(param);
            }
        }
        return Optional.empty();
    }

    /** Whether a read of this path can carry parameters at all. */
    public boolean parameterised() {
        return !params.isEmpty();
    }

    /**
     * The folders this path implies, outermost first.
     *
     * <p>They exist nowhere else: a mount's folders are whatever its listings
     * say, and an endpoint below one nobody lists would be unreachable by
     * browsing even though it answers perfectly well when addressed.
     */
    List<String> folders() {
        String[] segments = path.split("/");
        List<String> folders = new ArrayList<>(segments.length - 1);
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < segments.length - 1; i++) {
            if (i > 0) {
                current.append('/');
            }
            current.append(segments[i]);
            folders.add(current.toString());
        }
        return folders;
    }

    /** The last segment of {@link #path()} — the file name a listing shows. */
    public String fileName() {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }

    /** The folder holding this endpoint, {@code ""} for the mount root. */
    public String folder() {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? "" : path.substring(0, slash);
    }

    /** Never used for equality; here so a log line reads. */
    @Override
    public String toString() {
        return path + (params.isEmpty() ? "" : names());
    }

    private String names() {
        StringBuilder sb = new StringBuilder("?");
        for (int i = 0; i < params.size(); i++) {
            if (i > 0) {
                sb.append('&');
            }
            sb.append(params.get(i).name()).append('=');
        }
        return sb.toString();
    }
}
