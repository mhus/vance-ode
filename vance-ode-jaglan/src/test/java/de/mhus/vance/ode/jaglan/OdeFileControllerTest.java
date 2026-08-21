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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
                    OdeFileAccess.READ_ONLY, false, null, Duration.ofMinutes(5), 10L, null);
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

    private static class SearchingSource implements FileSource {

        int lastLimit;

        @Override public OdeFileCapabilities capabilities() {
            return new OdeFileCapabilities(
                    OdeFileAccess.READ_ONLY, true, null, Duration.ofMinutes(5), null, null);
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
