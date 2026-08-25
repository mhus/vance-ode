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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import de.mhus.vance.ode.jaglan.FileSource;
import de.mhus.vance.ode.jaglan.OdeFileCapabilities;
import de.mhus.vance.ode.jaglan.OdeFileEntry;
import de.mhus.vance.ode.jaglan.OdeQuery;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

/**
 * A {@link FileSource} with computed paths on top of the one it wraps.
 *
 * <p><b>Additive.</b> Everything the wrapped source serves, it keeps serving:
 * listings, stats, reads, writes and search pass straight through. What this adds
 * is a set of {@link MountEndpoint}s at paths of their own, plus a description of
 * them at {@link #API_PATH}. A source that has no endpoints yet can be wrapped
 * anyway and nothing about it changes.
 *
 * <pre>{@code
 * @Bean
 * FileSource archiveFiles(ArchiveFileSource archive, List<MountEndpoint> endpoints) {
 *     return new EndpointFileSource(archive, "The news archive, …", endpoints);
 * }
 * }</pre>
 *
 * <h2>Three things it does that are not obvious</h2>
 *
 * <p><b>It invents the folders.</b> An endpoint at {@code reports/trends.yaml}
 * needs a {@code reports} folder to be listed under, and the wrapped source has
 * never heard of it. Those folders are derived from the endpoint paths and served
 * from here — without them the endpoint would answer when addressed and be
 * invisible to anyone browsing. Which folder that is, is entirely the source's
 * choice: nothing here reserves a name or expects one, and where an endpoint
 * path collides with a wrapped one the endpoint wins and the collision is
 * logged.
 *
 * <p><b>It declares {@code supportsQuery} for the whole mount.</b> There is one
 * such flag per mount and endpoints are a handful of paths inside it, so the flag
 * says "somewhere in here" rather than "everywhere in here". A query against an
 * ordinary file therefore reaches the wrapped source, whose default refuses it —
 * which is the right answer and the reason this class does not swallow it.
 *
 * <p><b>{@link #API_PATH} is not listed.</b> It is the one path here that no
 * listing mentions, and that is deliberate: it describes the mount rather than
 * belonging to it, and a description that shows up as a file in the tree is a
 * file somebody has to explain. The reader can still read it — an unlisted
 * mounted path is stat'ed on demand when addressed — and the row it keeps for it
 * is derived from the path, so it survives being pruned by a listing that does
 * not mention it and comes back identical on the next read.
 */
@Slf4j
public class EndpointFileSource implements FileSource {

    /**
     * Where the mount describes itself.
     *
     * <p>Fixed rather than configurable, and at the root rather than beside the
     * endpoints: it is the one address a caller has to be able to guess. A
     * discoverable location that must first be discovered is not one.
     */
    public static final String API_PATH = "_api.yaml";

    private static final String YAML_MIME = "application/yaml";

    private final FileSource delegate;
    private final @Nullable String about;

    /** By path, in declaration order — the order the description lists them in. */
    private final Map<String, MountEndpoint> endpoints;

    /** Every folder the endpoint paths imply, including nested ones. */
    private final Set<String> folders;

    private final MountEndpoint api;

    public EndpointFileSource(FileSource delegate, List<MountEndpoint> endpoints) {
        this(delegate, null, endpoints);
    }

    public EndpointFileSource(
            FileSource delegate, @Nullable String about, List<MountEndpoint> endpoints) {
        if (delegate == null) {
            throw new IllegalArgumentException("a file source to wrap is required");
        }
        this.delegate = delegate;
        this.about = about == null || about.isBlank() ? null : about.strip();
        this.endpoints = index(endpoints);
        this.folders = foldersOf(this.endpoints.values());
        this.api = new ApiSpecEndpoint(this::describe);
        checkRenderings(this.endpoints);
        log.info("Jaglan endpoints: {} computed path(s) plus /{} — {}",
                this.endpoints.size(), API_PATH, this.endpoints.keySet());
    }

    /**
     * Refuses a duplicate path at construction.
     *
     * <p>Two endpoints at one address is a mistake with no sensible resolution,
     * and picking one silently means the other's parameters are declared and
     * never read.
     */
    private static Map<String, MountEndpoint> index(List<MountEndpoint> endpoints) {
        Map<String, MountEndpoint> byPath = new LinkedHashMap<>();
        if (endpoints == null) {
            return byPath;
        }
        for (MountEndpoint endpoint : endpoints) {
            EndpointSpec spec = endpoint.spec();
            if (spec == null) {
                throw new IllegalArgumentException(
                        endpoint.getClass().getName() + " declares no spec");
            }
            if (API_PATH.equals(spec.path())) {
                throw new IllegalArgumentException("'" + API_PATH
                        + "' is where the mount describes itself and cannot be an endpoint");
            }
            MountEndpoint clash = byPath.put(spec.path(), endpoint);
            if (clash != null) {
                throw new IllegalArgumentException("two endpoints claim '" + spec.path()
                        + "': " + clash.getClass().getName() + " and "
                        + endpoint.getClass().getName());
            }
        }
        return byPath;
    }

    /**
     * Checks every {@code renderingOf} at construction.
     *
     * <p>Two things, both of which would otherwise show up as a description that
     * quietly disagrees with the endpoints: a rendering of a path nobody serves
     * would vanish from the description entirely, and a rendering that accepts
     * different parameters than the entry it appears under would document the
     * wrong ones for it. There is no sensible runtime handling for either, and a
     * declaration is written once — so this throws.
     */
    private static void checkRenderings(Map<String, MountEndpoint> endpoints) {
        for (MountEndpoint endpoint : endpoints.values()) {
            EndpointSpec spec = endpoint.spec();
            String target = spec.renderingOf();
            if (target == null) {
                continue;
            }
            MountEndpoint primary = endpoints.get(target);
            if (primary == null) {
                throw new IllegalArgumentException("'" + spec.path()
                        + "' is declared a rendering of '" + target
                        + "', which no endpoint serves");
            }
            if (!primary.spec().standsAlone()) {
                throw new IllegalArgumentException("'" + spec.path()
                        + "' is a rendering of '" + target + "', which is itself a rendering "
                        + "of '" + primary.spec().renderingOf() + "' — formats are one level, "
                        + "so point at the endpoint that stands alone");
            }
            if (!primary.spec().params().equals(spec.params())) {
                throw new IllegalArgumentException("'" + spec.path()
                        + "' is a rendering of '" + target + "' and must accept exactly its "
                        + "parameters: it would be described under that entry's parameter "
                        + "list, and a format that takes different ones makes it wrong");
            }
        }
    }

    private static Set<String> foldersOf(Iterable<MountEndpoint> endpoints) {
        Set<String> folders = new LinkedHashSet<>();
        for (MountEndpoint endpoint : endpoints) {
            folders.addAll(endpoint.spec().folders());
        }
        return folders;
    }

    // ── capabilities ─────────────────────────────────────────────────

    /**
     * The wrapped source's, with {@code supportsQuery} raised once an endpoint
     * declares a parameter.
     *
     * <p>Not raised unconditionally: a wrapper around endpoints that take no
     * parameters would otherwise tell the reader it serves parameterised reads,
     * and every one of them would come back refused.
     */
    @Override
    public OdeFileCapabilities capabilities() {
        OdeFileCapabilities inner = delegate.capabilities();
        boolean query = inner.supportsQuery() || anyParameterised();
        if (query == inner.supportsQuery()) {
            return inner;
        }
        return new OdeFileCapabilities(inner.access(), inner.canSearch(), inner.itemCount(),
                inner.metadataTtl(), inner.maxBytes(), true, inner.displayName());
    }

    private boolean anyParameterised() {
        for (MountEndpoint endpoint : endpoints.values()) {
            if (endpoint.spec().parameterised()) {
                return true;
            }
        }
        return false;
    }

    // ── stat ─────────────────────────────────────────────────────────

    @Override
    public Optional<OdeFileEntry> stat(String path) {
        String clean = normalise(path);
        if (API_PATH.equals(clean)) {
            return Optional.of(entryFor(api.spec()));
        }
        MountEndpoint endpoint = endpoints.get(clean);
        if (endpoint != null) {
            return Optional.of(entryFor(endpoint.spec()));
        }
        if (folders.contains(clean)) {
            return Optional.of(OdeFileEntry.folder(clean));
        }
        return delegate.stat(clean);
    }

    /**
     * The metadata of a computed path.
     *
     * <p><b>Size 0 and no etag, both deliberately.</b> The contract reads 0 as
     * "cannot say cheaply", which is exactly the situation: the length of an
     * answer that does not exist yet is unknowable without computing it, and
     * computing it here would do the work twice on every listing. An etag has the
     * same problem and a worse failure — a stale one hands back one view in place
     * of another.
     */
    private static OdeFileEntry entryFor(EndpointSpec spec) {
        return new OdeFileEntry(
                spec.path(), false, 0, spec.mimeType(), null, null, spec.title());
    }

    // ── list ─────────────────────────────────────────────────────────

    /**
     * The wrapped source's children, plus the computed ones that belong here.
     *
     * <p>A folder of ours answers on its own — the wrapped source is not asked
     * about a path it never heard of. The root is the one place both are merged,
     * and there the computed entries win a name collision, which is the reason
     * for putting endpoints under a prefix of their own.
     */
    @Override
    public List<OdeFileEntry> list(String path) {
        String clean = normalise(path);
        List<OdeFileEntry> mine = childrenOf(clean);
        if (clean.isEmpty()) {
            return merge(delegate.list(clean), mine);
        }
        return mine.isEmpty() ? delegate.list(clean) : mine;
    }

    /** Endpoints and folders directly under {@code folder}. */
    private List<OdeFileEntry> childrenOf(String folder) {
        List<OdeFileEntry> entries = new ArrayList<>();
        for (String nested : folders) {
            if (folder.equals(parentOf(nested))) {
                entries.add(OdeFileEntry.folder(nested));
            }
        }
        for (MountEndpoint endpoint : endpoints.values()) {
            EndpointSpec spec = endpoint.spec();
            if (folder.equals(spec.folder())) {
                entries.add(entryFor(spec));
            }
        }
        // API_PATH is absent by omission and not by filtering — it is not in
        // either collection. Stated here because its absence is a decision.
        return entries;
    }

    private static List<OdeFileEntry> merge(List<OdeFileEntry> inherited,
            List<OdeFileEntry> mine) {
        if (mine.isEmpty()) {
            return inherited;
        }
        Set<String> claimed = new LinkedHashSet<>();
        for (OdeFileEntry entry : mine) {
            claimed.add(entry.path());
        }
        List<OdeFileEntry> merged = new ArrayList<>(inherited.size() + mine.size());
        for (OdeFileEntry entry : inherited) {
            if (claimed.contains(entry.path())) {
                log.warn("Jaglan endpoints: '{}' is served by an endpoint and by the wrapped "
                        + "source; the endpoint wins", entry.path());
                continue;
            }
            merged.add(entry);
        }
        merged.addAll(mine);
        return merged;
    }

    // ── read ─────────────────────────────────────────────────────────

    @Override
    public InputStream open(String path) {
        return open(path, OdeQuery.EMPTY);
    }

    /**
     * A computed answer for a computed path, the wrapped source's bytes for
     * anything else.
     *
     * <p>A query aimed at an ordinary file is handed to the wrapped source
     * untouched, so its own refusal is what the caller gets. Answering that here
     * would be this class deciding something about paths it knows nothing about.
     */
    @Override
    public InputStream open(String path, OdeQuery query) {
        String clean = normalise(path);
        MountEndpoint endpoint = API_PATH.equals(clean) ? api : endpoints.get(clean);
        if (endpoint == null) {
            return delegate.open(clean, query == null ? OdeQuery.EMPTY : query);
        }
        CallContext ctx = CallContext.of(endpoint.spec(), query == null ? OdeQuery.EMPTY : query);
        endpoint.handle(ctx);
        // answer() throws when the endpoint produced nothing, which is a bug in
        // the endpoint and belongs reported as a failure rather than served as an
        // empty file.
        return new ByteArrayInputStream(ctx.answer());
    }

    // ── pass-through ─────────────────────────────────────────────────

    /**
     * The wrapped source's search, unchanged.
     *
     * <p>Endpoints are deliberately not searchable. A search returns paths, and a
     * path to a computed view without its parameters is the plain read — so a hit
     * would point at something other than what matched.
     */
    @Override
    public List<OdeFileEntry> search(String query, int limit) {
        return delegate.search(query, limit);
    }

    @Override
    public OdeFileEntry write(String path, InputStream content) {
        String clean = normalise(path);
        if (isComputed(clean)) {
            throw new UnsupportedOperationException(
                    "'" + clean + "' is computed and cannot be written");
        }
        return delegate.write(clean, content);
    }

    @Override
    public void delete(String path) {
        String clean = normalise(path);
        if (isComputed(clean)) {
            throw new UnsupportedOperationException(
                    "'" + clean + "' is computed and cannot be deleted");
        }
        delegate.delete(clean);
    }

    private boolean isComputed(String path) {
        return API_PATH.equals(path) || endpoints.containsKey(path) || folders.contains(path);
    }

    // ── the description ──────────────────────────────────────────────

    /**
     * The mount as one document — what {@link #API_PATH} serves, and public
     * because a source may want to show the same thing somewhere else.
     */
    public ApiDescription describe() {
        OdeFileCapabilities caps = capabilities();
        // One entry per endpoint that stands alone, with its other formats
        // folded in. A rendering has no entry of its own: it would repeat the
        // whole parameter list under a second name, and a caller comparing the
        // two would have to work out that they are one report.
        List<ApiDescription.Endpoint> described = new ArrayList<>(endpoints.size());
        for (MountEndpoint endpoint : endpoints.values()) {
            EndpointSpec spec = endpoint.spec();
            if (spec.standsAlone()) {
                described.add(ApiDescription.Endpoint.of(spec, renderingsOf(spec.path())));
            }
        }
        return new ApiDescription(
                caps.displayName(),
                about,
                caps.access().name(),
                caps.canSearch(),
                USAGE,
                described);
    }

    /** The other formats of {@code path}, in declaration order. */
    private List<EndpointSpec> renderingsOf(String path) {
        List<EndpointSpec> renderings = new ArrayList<>(2);
        for (MountEndpoint endpoint : endpoints.values()) {
            if (path.equals(endpoint.spec().renderingOf())) {
                renderings.add(endpoint.spec());
            }
        }
        return renderings;
    }

    /**
     * The calling convention, spelled out.
     *
     * <p>Every line here is something a caller would otherwise have to guess,
     * and three of them are things it would guess wrong: that a plain read is
     * allowed at all, that the parameterised form is not listed anywhere, and
     * that an undeclared parameter is refused rather than ignored.
     */
    private static final List<String> USAGE = List.of(
            "Read one of these paths like any other file in this mount.",
            "Parameters go on the path as a query string: "
                    + "path/to/endpoint.yaml?name=value&other=value.",
            "Reading a path without parameters is allowed and answers with the "
                    + "declared defaults.",
            "Only declared parameters are accepted. An undeclared one, a value of "
                    + "the wrong type, or a missing required one is refused with a "
                    + "reason — never silently ignored.",
            "A parameterised read appears in no listing and is never cached. "
                    + "The unparameterised path is the only one a directory shows.",
            "A path under 'alsoAt' is the same answer in another format and takes "
                    + "the same parameters, which are the ones listed above it.",
            "This document is not itself listed. Its path is fixed: " + API_PATH + ".");

    // ── paths ────────────────────────────────────────────────────────

    private static String normalise(@Nullable String path) {
        if (path == null) {
            return "";
        }
        String clean = path.strip();
        while (clean.startsWith("/")) {
            clean = clean.substring(1);
        }
        while (clean.endsWith("/")) {
            clean = clean.substring(0, clean.length() - 1);
        }
        return clean;
    }

    private static String parentOf(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? "" : path.substring(0, slash);
    }
}
