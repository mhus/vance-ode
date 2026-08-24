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
package de.mhus.vance.ode.jaglan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Here the wire contract <i>is</i> the deliverable, so these tests go through
 * the HTTP layer: paths, status codes, parameter binding and what the source
 * actually receives. A source implementer breaks on any of those, not on our
 * internal method signatures.
 */
class OdeFileControllerTest {

    private static final String PATH = "/ode/files";

    private RecordingSource source;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        source = new RecordingSource();
        mvc = mvc(source);
    }

    private static MockMvc mvc(FileSource source) {
        return MockMvcBuilders
                .standaloneSetup(new OdeFileController(source, new VanceOdeJaglanProperties()))
                .addPlaceholderValue("vance.ode.jaglan.path", PATH)
                .build();
    }

    // ─── capabilities ───────────────────────────────────────────────────

    @Test
    void capabilities_travelWithAnIso8601Duration() throws Exception {
        // Self-describing on the wire matters more in a contract between two
        // systems than brevity does.
        mvc.perform(get(PATH + "/capabilities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access").value("READ_ONLY"))
                .andExpect(jsonPath("$.metadataTtl").value("PT5M"));
    }

    // ─── stat: gone versus broken ───────────────────────────────────────

    @Test
    void stat_knownFile_carriesTheMetadataTheReaderStores() throws Exception {
        mvc.perform(get(PATH + "/stat").param("path", "books/dune.pdf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.path").value("books/dune.pdf"))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.etag").value("etag-1"))
                .andExpect(jsonPath("$.folder").value(false));
    }

    @Test
    void stat_unknownFile_is404AndNot500() throws Exception {
        // The reader treats 404 as authoritative and forgets its metadata row;
        // a 500 keeps it. Confusing the two makes an outage look like deletion.
        mvc.perform(get(PATH + "/stat").param("path", "nope.pdf"))
                .andExpect(status().isNotFound());
    }

    @Test
    void stat_sourceFailure_is500AndNot404() throws Exception {
        MockMvc failing = mvc(new FailingSource());

        failing.perform(get(PATH + "/stat").param("path", "x.pdf"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("source_failed"));
    }

    // ─── list ───────────────────────────────────────────────────────────

    @Test
    void list_withoutPath_asksForTheRoot() throws Exception {
        mvc.perform(get(PATH + "/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].path").value("books"))
                .andExpect(jsonPath("$[0].folder").value(true));

        assertThat(source.listedPaths).containsExactly("");
    }

    @Test
    void list_normalisesTheInboundPath() throws Exception {
        mvc.perform(get(PATH + "/list").param("path", "/books/"))
                .andExpect(status().isOk());

        // A source should never have to strip slashes itself.
        assertThat(source.listedPaths).containsExactly("books");
    }

    // ─── content streams ────────────────────────────────────────────────

    @Test
    void content_returnsRawBytesWithTypeLengthAndEtag() throws Exception {
        var result = mvc.perform(get(PATH + "/content").param("path", "books/dune.pdf"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"etag-1\""))
                .andExpect(header().string("Content-Length", "5"))
                .andReturn();

        // No JSON envelope, no base64 — a mount exists so a large file needs
        // no copy on either side.
        assertThat(result.getResponse().getContentAsString()).isEqualTo("spice");
        assertThat(result.getResponse().getContentType()).isEqualTo("application/pdf");
    }

    @Test
    void content_ofAFolder_is404() throws Exception {
        mvc.perform(get(PATH + "/content").param("path", "books"))
                .andExpect(status().isNotFound());
    }

    @Test
    void content_aboveTheDeclaredLimit_isRefusedWithoutOpeningTheFile() throws Exception {
        MockMvc capped = mvc(new CappedSource());

        capped.perform(get(PATH + "/content").param("path", "huge.bin"))
                .andExpect(status().isPayloadTooLarge());
    }

    @Test
    void content_withAnUnparseableMimeType_isServedAndLeavesNoOpenStream() throws Exception {
        // A source may legitimately have an empty mime column. Parsing that as
        // a media type throws, and doing it after open() would answer 500 and
        // strand a file handle in the host process on every retry.
        UntypedSource untyped = new UntypedSource();

        var result = mvc(untyped).perform(get(PATH + "/content").param("path", "x.bin"))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getContentType())
                .isEqualTo("application/octet-stream");
        assertThat(result.getResponse().getContentAsString()).isEqualTo("spice");
        assertThat(untyped.opened).isNotNull();
        assertThat(untyped.opened.closed).isTrue();
    }

    // ─── write and delete ───────────────────────────────────────────────

    @Test
    void write_toAReadOnlySource_is405AndNot403() throws Exception {
        // Read-only is a property of the source, not of who is asking — a 403
        // would send a reader looking for a credential problem.
        mvc.perform(put(PATH + "/content").param("path", "x.txt")
                        .content("body".getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error").value("read_only"));

        assertThat(source.written).isEmpty();
    }

    @Test
    void delete_onAReadOnlySource_is405() throws Exception {
        mvc.perform(delete(PATH + "/content").param("path", "x.txt"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void write_toAWritableSource_handsTheStreamThroughAndReturnsTheNewEntry()
            throws Exception {
        WritableSource writable = new WritableSource();

        mvc(writable).perform(put(PATH + "/content").param("path", "notes/new.txt")
                        .content("written".getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.path").value("notes/new.txt"))
                .andExpect(jsonPath("$.size").value(7));

        assertThat(writable.receivedBody).isEqualTo("written");
    }

    @Test
    void delete_onAWritableSource_is204() throws Exception {
        WritableSource writable = new WritableSource();

        mvc(writable).perform(delete(PATH + "/content").param("path", "x.txt"))
                .andExpect(status().isNoContent());

        assertThat(writable.deleted).containsExactly("x.txt");
    }

    @Test
    void declaredWritableButUnimplemented_isRefusedNotAFailure() throws Exception {
        // Reached only when the declaration and the implementation disagree;
        // it must still not look like the source fell over.
        mvc(new LyingSource()).perform(delete(PATH + "/content").param("path", "x.txt"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error").value("read_only"));
    }

    @Test
    void refusal_carriesAnAllowHeader() throws Exception {
        // RFC 9110 requires it on a 405. The reader only reads the status; a
        // proxy or a browser in between does not.
        mvc.perform(delete(PATH + "/content").param("path", "x.txt"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", "GET"));
    }

    @Test
    void unsupportedOperationOutOfAReadPath_is500AndNotARefusal() throws Exception {
        // UnsupportedOperationException is what every immutable collection in
        // the JDK throws, so out of list() it is an ordinary bug inside a
        // source. Answered as 405 the reader would read it as a stable refusal
        // and give up on a mount that is merely broken.
        mvc(new ImmutableSortingSource()).perform(get(PATH + "/list").param("path", "books"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("source_failed"));
    }

    // ─── search ─────────────────────────────────────────────────────────

    @Test
    void search_undeclared_isAnEmptyListNotA404() throws Exception {
        // A reader holding a stale capabilities response should find the
        // feature gone, not the endpoint broken.
        mvc.perform(get(PATH + "/search").param("q", "dune"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void search_declared_isClampedToTheOperatorCeiling() throws Exception {
        SearchingSource searching = new SearchingSource();

        mvc(searching).perform(get(PATH + "/search")
                        .param("q", "dune").param("limit", "9999"))
                .andExpect(status().isOk());

        assertThat(searching.lastLimit).isEqualTo(200);
    }

    @Test
    void search_withoutQuery_is400() throws Exception {
        mvc(new SearchingSource()).perform(get(PATH + "/search").param("q", "  "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("bad_request"));
    }

    @Test
    void search_withACeilingOfZero_readsItAsUnsetRatherThanAsOneRow() throws Exception {
        // Centauri reads a non-positive ceiling as "not configured" and says
        // why; taken literally here it would make a configuration typo look
        // like a source that only ever has a single match.
        SearchingSource searching = new SearchingSource();
        VanceOdeJaglanProperties unset = new VanceOdeJaglanProperties();
        unset.setMaxSearchLimit(0);
        MockMvc uncapped = MockMvcBuilders
                .standaloneSetup(new OdeFileController(searching, unset))
                .addPlaceholderValue("vance.ode.jaglan.path", PATH)
                .build();

        uncapped.perform(get(PATH + "/search").param("q", "dune").param("limit", "50"))
                .andExpect(status().isOk());

        assertThat(searching.lastLimit).isEqualTo(50);
    }

    // ─── path hygiene ───────────────────────────────────────────────────

    @Test
    void traversal_isRefusedAtTheEndpoint() throws Exception {
        // Second of the two checks: the reader rejects it too, but a public
        // endpoint cannot rely on its callers being well-behaved.
        mvc.perform(get(PATH + "/stat").param("path", "../../etc/passwd"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("bad_request"));
        assertThat(source.statedPaths).isEmpty();
    }

    @Test
    void singleDotSegment_isAlsoRefused() throws Exception {
        mvc.perform(get(PATH + "/list").param("path", "./books"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void backslashTraversal_isRefused() throws Exception {
        // One segment as far as a '/' split is concerned, and a walk out of the
        // root as far as Windows is concerned. The javadoc promises a source
        // can resolve whatever it is handed against its own root, so this end
        // has to defend the separator it does not itself use.
        mvc.perform(get(PATH + "/stat").param("path", "..\\..\\etc\\passwd"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("bad_request"));
        assertThat(source.statedPaths).isEmpty();
    }

    @Test
    void uncPath_isRefused() throws Exception {
        mvc.perform(get(PATH + "/stat").param("path", "\\\\server\\share\\secret.txt"))
                .andExpect(status().isBadRequest());
        assertThat(source.statedPaths).isEmpty();
    }

    @Test
    void driveLetterPath_isRefused() throws Exception {
        // Path.resolve with an absolute argument replaces the base, so this
        // leaves the root without a single '..' in it.
        mvc.perform(get(PATH + "/content").param("path", "C:/Windows/win.ini"))
                .andExpect(status().isBadRequest());
        assertThat(source.statedPaths).isEmpty();
    }

    @Test
    void emptySegment_isRefused() throws Exception {
        mvc.perform(get(PATH + "/list").param("path", "books//dune"))
                .andExpect(status().isBadRequest());
    }

    // ─── parameterised reads ────────────────────────────────────────────

    @Test
    void content_withAQuery_againstASourceThatDeclaredNone_isRefused() throws Exception {
        // 405, not 400: whether parameters are served is a property of the
        // source, and a 4xx about the request would send the caller looking
        // for a mistake in their parameters. With Allow and a reason, like
        // every other refusal here — a bare status leaves the reader reporting
        // a refusal it cannot explain, and a proxy in between seeing a 405
        // that violates RFC 9110.
        mvc.perform(get(PATH + "/content").param("path", "books/dune.pdf").param("from", "2026-01"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", "GET"))
                .andExpect(jsonPath("$.error").value("query_unsupported"));
    }

    @Test
    void content_declaredButUnimplemented_is405AndNot500() throws Exception {
        // The distinction that matters: 500 classifies as transient on the
        // reader side, so a permanent misconfiguration would be retried
        // forever while stale metadata rows are kept alive.
        MockMvc broken = mvc(new DeclaredButUnimplementedSource());

        broken.perform(get(PATH + "/content").param("path", "a.yaml").param("from", "2026-01"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error").value("query_unsupported"));
    }

    @Test
    void content_computedViewAboveTheDeclaredLimit_isAborted() throws Exception {
        // maxBytes cannot be checked in advance for content that does not
        // exist yet, so it is enforced on the way out. Without this the one
        // case that can produce arbitrary bytes would be the only unbounded
        // one — and with Content-Length suppressed, nothing downstream could
        // notice either.
        MockMvc capped = mvc(new CappedQuerySource());

        assertThatThrownBy(() -> capped.perform(
                get(PATH + "/content").param("path", "a.yaml").param("from", "2026-01")))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("maxBytes");
    }

    @Test
    void odeQuery_keepsTheOrderParametersArrivedIn() {
        // names() promises order, and a source may derive a cache key from it.
        // Map.copyOf would randomise that per JVM run.
        Map<String, List<String>> raw = new LinkedHashMap<>();
        raw.put("zulu", List.of("1"));
        raw.put("alpha", List.of("2"));
        raw.put("mike", List.of("3"));

        assertThat(new OdeQuery(raw).names()).containsExactly("zulu", "alpha", "mike");
    }

    @Test
    void content_withAQuery_reachesTheSourceWithoutThePathParameter() throws Exception {
        QuerySource source = new QuerySource();

        mvc(source).perform(get(PATH + "/content")
                        .param("path", "analysis.yaml")
                        .param("from", "2026-01")
                        .param("tag", "a").param("tag", "b"))
                .andExpect(status().isOk());

        // 'path' addresses the file; a source that saw it would be reading the
        // address as data.
        assertThat(source.received.names()).containsExactly("from", "tag");
        assertThat(source.received.first("from")).isEqualTo("2026-01");
        // Repeated keys survive: a multiple-choice input produces them, and
        // half a chosen set delivered silently is the failure this avoids.
        assertThat(source.received.all("tag")).containsExactly("a", "b");
    }

    @Test
    void content_withAQuery_sendsNeitherLengthNorEtagOfThePlainFile() throws Exception {
        // Both describe the unparameterised file. A stale Content-Length
        // truncates the computed answer; a stale ETag lets a cache hand back
        // one view in place of another.
        mvc(new QuerySource()).perform(get(PATH + "/content")
                        .param("path", "analysis.yaml").param("from", "2026-01"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("ETag"))
                .andExpect(header().doesNotExist("Content-Length"))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void content_withoutAQuery_stillCarriesLengthAndEtag() throws Exception {
        mvc.perform(get(PATH + "/content").param("path", "books/dune.pdf"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"etag-1\""));
    }

    /** Declares parameterised reads and never implements them. */
    private static class DeclaredButUnimplementedSource implements FileSource {
        @Override public OdeFileCapabilities capabilities() {
            return new OdeFileCapabilities(
                    OdeFileAccess.READ_ONLY, false, null, Duration.ofMinutes(5), null, true, null);
        }
        @Override public Optional<OdeFileEntry> stat(String path) {
            return Optional.of(OdeFileEntry.file(path, 5, "text/yaml", null));
        }
        @Override public List<OdeFileEntry> list(String path) {
            return List.of();
        }
        @Override public InputStream open(String path) {
            return new ByteArrayInputStream("plain".getBytes(StandardCharsets.UTF_8));
        }
        // open(path, query) deliberately not overridden — the SPI default throws.
    }

    /** Declares a small ceiling and then blows through it. */
    private static class CappedQuerySource implements FileSource {
        @Override public OdeFileCapabilities capabilities() {
            return new OdeFileCapabilities(
                    OdeFileAccess.READ_ONLY, false, null, Duration.ofMinutes(5), 8L, true, null);
        }
        @Override public Optional<OdeFileEntry> stat(String path) {
            return Optional.of(OdeFileEntry.file(path, 5, "text/yaml", null));
        }
        @Override public List<OdeFileEntry> list(String path) {
            return List.of();
        }
        @Override public InputStream open(String path) {
            return new ByteArrayInputStream("plain".getBytes(StandardCharsets.UTF_8));
        }
        @Override public InputStream open(String path, OdeQuery query) {
            return new ByteArrayInputStream(
                    "far more than eight bytes".getBytes(StandardCharsets.UTF_8));
        }
    }

    /** Declares parameterised reads and records what it was handed. */
    private static class QuerySource implements FileSource {

        OdeQuery received = OdeQuery.EMPTY;

        @Override
        public OdeFileCapabilities capabilities() {
            return new OdeFileCapabilities(
                    OdeFileAccess.READ_ONLY, false, null, Duration.ofMinutes(5), null, true, null);
        }

        @Override
        public Optional<OdeFileEntry> stat(String path) {
            return Optional.of(OdeFileEntry.file(path, 5, "text/yaml", "etag-plain"));
        }

        @Override
        public List<OdeFileEntry> list(String path) {
            return List.of();
        }

        @Override
        public InputStream open(String path) {
            return new ByteArrayInputStream("plain".getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public InputStream open(String path, OdeQuery query) {
            received = query;
            return new ByteArrayInputStream("computed:".getBytes(StandardCharsets.UTF_8));
        }
    }

    // ─── test doubles ───────────────────────────────────────────────────

    /** A read-only source with one folder and one file. */
    private static class RecordingSource implements FileSource {

        final List<String> statedPaths = new ArrayList<>();
        final List<String> listedPaths = new ArrayList<>();
        final List<String> written = new ArrayList<>();

        @Override
        public OdeFileCapabilities capabilities() {
            return OdeFileCapabilities.readOnly();
        }

        @Override
        public Optional<OdeFileEntry> stat(String path) {
            statedPaths.add(path);
            if ("books/dune.pdf".equals(path)) {
                return Optional.of(OdeFileEntry.file(path, 5, "application/pdf", "etag-1"));
            }
            if ("books".equals(path) || path.isEmpty()) {
                return Optional.of(OdeFileEntry.folder(path));
            }
            return Optional.empty();
        }

        @Override
        public List<OdeFileEntry> list(String path) {
            listedPaths.add(path);
            return List.of(OdeFileEntry.folder("books"));
        }

        @Override
        public InputStream open(String path) {
            return new ByteArrayInputStream("spice".getBytes(StandardCharsets.UTF_8));
        }
    }

    private static class FailingSource implements FileSource {
        @Override public OdeFileCapabilities capabilities() {
            return OdeFileCapabilities.readOnly();
        }
        @Override public Optional<OdeFileEntry> stat(String path) {
            throw new IllegalStateException("index offline");
        }
        @Override public List<OdeFileEntry> list(String path) {
            throw new IllegalStateException("index offline");
        }
        @Override public InputStream open(String path) {
            throw new IllegalStateException("index offline");
        }
    }

    private static class CappedSource implements FileSource {
        @Override public OdeFileCapabilities capabilities() {
            return new OdeFileCapabilities(
                    OdeFileAccess.READ_ONLY, false, null, Duration.ofMinutes(5), 10L, false, null);
        }
        @Override public Optional<OdeFileEntry> stat(String path) {
            return Optional.of(OdeFileEntry.file(path, 5_000, "application/octet-stream", null));
        }
        @Override public List<OdeFileEntry> list(String path) {
            return List.of();
        }
        @Override public InputStream open(String path) {
            throw new AssertionError("must not be opened past the declared limit");
        }
    }

    private static class WritableSource implements FileSource {

        String receivedBody = "";
        final List<String> deleted = new ArrayList<>();

        @Override public OdeFileCapabilities capabilities() {
            return OdeFileCapabilities.readWrite();
        }
        @Override public Optional<OdeFileEntry> stat(String path) {
            return Optional.of(OdeFileEntry.file(path, 1, "text/plain", null));
        }
        @Override public List<OdeFileEntry> list(String path) {
            return List.of();
        }
        @Override public InputStream open(String path) {
            return new ByteArrayInputStream(new byte[0]);
        }
        @Override public OdeFileEntry write(String path, InputStream content) {
            try {
                receivedBody = new String(content.readAllBytes(), StandardCharsets.UTF_8);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            return OdeFileEntry.file(path, receivedBody.length(), "text/plain", "e2");
        }
        @Override public void delete(String path) {
            deleted.add(path);
        }
    }

    /** Declares read-write but implements neither — the disagreement case. */
    private static class LyingSource implements FileSource {
        @Override public OdeFileCapabilities capabilities() {
            return OdeFileCapabilities.readWrite();
        }
        @Override public Optional<OdeFileEntry> stat(String path) {
            return Optional.empty();
        }
        @Override public List<OdeFileEntry> list(String path) {
            return List.of();
        }
        @Override public InputStream open(String path) {
            return new ByteArrayInputStream(new byte[0]);
        }
    }

    /** Declares an empty mime type — legal, and not a parseable media type. */
    private static class UntypedSource implements FileSource {

        @Nullable TrackedStream opened;

        @Override public OdeFileCapabilities capabilities() {
            return OdeFileCapabilities.readOnly();
        }
        @Override public Optional<OdeFileEntry> stat(String path) {
            return Optional.of(OdeFileEntry.file(path, 5, "", "etag-1"));
        }
        @Override public List<OdeFileEntry> list(String path) {
            return List.of();
        }
        @Override public InputStream open(String path) {
            opened = new TrackedStream("spice");
            return opened;
        }
    }

    /** Sorts what {@code Stream.toList()} gave it — the ordinary source bug. */
    private static class ImmutableSortingSource implements FileSource {
        @Override public OdeFileCapabilities capabilities() {
            return OdeFileCapabilities.readOnly();
        }
        @Override public Optional<OdeFileEntry> stat(String path) {
            return Optional.empty();
        }
        @Override public List<OdeFileEntry> list(String path) {
            List<OdeFileEntry> rows = List.of(OdeFileEntry.folder("books"));
            rows.sort(null);
            return rows;
        }
        @Override public InputStream open(String path) {
            return new ByteArrayInputStream(new byte[0]);
        }
    }

    /** Says whether anybody closed it. */
    private static class TrackedStream extends ByteArrayInputStream {

        boolean closed;

        TrackedStream(String content) {
            super(content.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }

    private static class SearchingSource implements FileSource {

        int lastLimit;

        @Override public OdeFileCapabilities capabilities() {
            return new OdeFileCapabilities(
                    OdeFileAccess.READ_ONLY, true, null, Duration.ofMinutes(5), null, false, null);
        }
        @Override public Optional<OdeFileEntry> stat(String path) {
            return Optional.empty();
        }
        @Override public List<OdeFileEntry> list(String path) {
            return List.of();
        }
        @Override public InputStream open(String path) {
            return new ByteArrayInputStream(new byte[0]);
        }
        @Override public List<OdeFileEntry> search(String query, int limit) {
            lastLimit = limit;
            return List.of(OdeFileEntry.file("books/dune.pdf", 5, "application/pdf", null));
        }
    }
}
