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

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * A {@link KitSource} over a fixed set of files — the common case, and the one
 * worth making three lines long.
 *
 * <pre>{@code
 * @Bean
 * KitSource crmKit() {
 *     return StaticKitSource.fromClasspath("acme-crm", "kits/acme-crm");
 * }
 * }</pre>
 *
 * <p>„Static" is about where the files come from, not about what the caller
 * sees. A kit served this way still has per-installation values in it: the
 * placeholders in its {@code render:} files are filled by the reader. What this
 * class cannot do is vary the <i>file list</i> per caller — that is when an
 * application implements {@link KitSource} itself, and neither end has to change
 * for it.
 *
 * <p><b>Two loaders, two caching rules</b>, and the difference is intentional:
 * <ul>
 *   <li>{@link #fromClasspath} reads once and holds it. Files inside a jar do
 *       not change while the process runs.
 *   <li>{@link #fromDirectory} re-reads every time. A directory is where someone
 *       is editing, and „why is my change not showing up" is a worse cost than
 *       reading a few kilobytes per request.
 * </ul>
 *
 * <p>The revision is the content hash, so it moves exactly when the bytes move —
 * which is what {@link KitSource} asks for and what a version string cannot
 * promise.
 */
public final class StaticKitSource implements KitSource {

    private final String id;
    private final @Nullable String version;
    private final @Nullable String description;
    private final Supplier<Map<String, byte[]>> loader;

    /** Non-null once loaded when this source caches; always null when it does not. */
    private volatile @Nullable Map<String, byte[]> cached;
    private final boolean cache;

    private StaticKitSource(
            String id,
            @Nullable String version,
            @Nullable String description,
            Supplier<Map<String, byte[]>> loader,
            boolean cache) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("a kit source needs an id");
        }
        this.id = id;
        this.version = version;
        this.description = description;
        this.loader = loader;
        this.cache = cache;
    }

    /**
     * Kit packaged with this application, under {@code basePath} on the
     * classpath. Works inside a jar.
     */
    public static StaticKitSource fromClasspath(String id, String basePath) {
        return fromClasspath(id, basePath, null, null);
    }

    public static StaticKitSource fromClasspath(
            String id, String basePath, @Nullable String version, @Nullable String description) {
        String base = trimSlashes(basePath);
        return new StaticKitSource(
                id, version, description, () -> readClasspath(base), /*cache*/ true);
    }

    /** Kit in a directory on disk. Re-read per request, so edits show up. */
    public static StaticKitSource fromDirectory(String id, Path directory) {
        return fromDirectory(id, directory, null, null);
    }

    public static StaticKitSource fromDirectory(
            String id, Path directory, @Nullable String version, @Nullable String description) {
        return new StaticKitSource(
                id, version, description,
                () -> KitTreeHash.read(directory), /*cache*/ false);
    }

    @Override
    public OdeKitDeclaration declare() {
        return new OdeKitDeclaration(id, version, KitTreeHash.of(files()), description);
    }

    @Override
    public OdeKitBundle build(OdeKitBuildRequest request) {
        return new OdeKitBundle(files());
    }

    private Map<String, byte[]> files() {
        if (!cache) return loader.get();
        Map<String, byte[]> local = cached;
        if (local == null) {
            // Benign race: two threads may both load. The result is identical
            // and small, and a lock here would be the only contended thing in
            // an otherwise read-only path.
            local = loader.get();
            cached = local;
        }
        return local;
    }

    private static Map<String, byte[]> readClasspath(String base) {
        Map<String, byte[]> files = new TreeMap<>();
        var resolver = new PathMatchingResourcePatternResolver();
        Resource[] found;
        try {
            found = resolver.getResources("classpath*:" + base + "/**");
        } catch (IOException e) {
            throw new UncheckedIOException("failed to list the kit at classpath:" + base, e);
        }
        List<String> roots = classpathRoots(resolver, base);
        String marker = "/" + base + "/";
        for (Resource resource : found) {
            String uri;
            try {
                uri = resource.getURI().toString();
            } catch (IOException e) {
                throw new UncheckedIOException("failed to address a resource under " + base, e);
            }
            String relative = relativise(uri, roots, marker);
            if (relative == null) continue;
            // Directories come back as entries too, and a jar reports them with
            // a trailing slash while a filesystem reports them without — hence
            // readability rather than the name as the test.
            if (relative.isEmpty() || relative.endsWith("/") || !resource.isReadable()) continue;
            try (InputStream in = resource.getInputStream()) {
                files.put(relative, in.readAllBytes());
            } catch (IOException e) {
                throw new UncheckedIOException("failed to read " + uri, e);
            }
        }
        if (files.isEmpty()) {
            // A kit that resolves to nothing would be reported by the reader as
            // „delivered without a descriptor", which sends whoever debugs it
            // to the wrong end.
            throw new IllegalStateException(
                    "no files found at classpath:" + base + " — is the kit packaged?");
        }
        return files;
    }

    /**
     * The addresses {@code base} itself resolves to — one per classpath entry
     * that carries it.
     *
     * <p>Resolved so that a file's path inside the kit can be taken as what is
     * left after its own root, rather than guessed by looking for the base
     * name inside the address. Empty when the classpath entry has no directory
     * entry for it, which some jars do not; {@link #relativise} falls back for
     * that case.
     */
    private static List<String> classpathRoots(
            PathMatchingResourcePatternResolver resolver, String base) {
        Resource[] roots;
        try {
            roots = resolver.getResources("classpath*:" + base + "/");
        } catch (IOException e) {
            return List.of();
        }
        List<String> uris = new ArrayList<>(roots.length);
        for (Resource root : roots) {
            try {
                String uri = canonical(root.getURI().toString());
                uris.add(uri.endsWith("/") ? uri : uri + "/");
            } catch (IOException e) {
                // One unaddressable root does not invalidate the others.
                continue;
            }
        }
        return uris;
    }

    /**
     * {@code file:///x} and {@code file:/x} name the same thing, and the two
     * halves of this lookup are produced by different Spring resource types —
     * a classpath root arrives as a {@code UrlResource} and a walked file as a
     * {@code FileSystemResource}, which spell it differently.
     */
    private static String canonical(String uri) {
        return uri.startsWith("file:///") ? "file:/" + uri.substring("file:///".length()) : uri;
    }

    /**
     * The path of {@code uri} inside the kit, or null when it is not in it.
     *
     * <p>Matched against the resolved roots first, because searching for
     * {@code /<base>/} in the whole address folds a tree that repeats the base
     * name: with {@code base = "kit"}, both {@code kit/a.md} and
     * {@code kit/manuals/kit/a.md} would come out as {@code a.md} and one of
     * them would silently replace the other — including in the revision hash,
     * so nothing would look wrong from either end.
     */
    private static @Nullable String relativise(String uri, List<String> roots, String marker) {
        String canonical = canonical(uri);
        for (String root : roots) {
            if (canonical.startsWith(root)) {
                return canonical.substring(root.length());
            }
        }
        // No root resolved (a jar without directory entries, an unusual
        // protocol): the last occurrence of the marker is the best guess left,
        // and it is the one that was always used.
        int at = canonical.lastIndexOf(marker);
        return at < 0 ? null : canonical.substring(at + marker.length());
    }

    private static String trimSlashes(String path) {
        String s = path == null ? "" : path.trim();
        while (s.startsWith("/")) s = s.substring(1);
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        if (s.isEmpty()) {
            throw new IllegalArgumentException("a classpath kit needs a base path");
        }
        return s;
    }
}
