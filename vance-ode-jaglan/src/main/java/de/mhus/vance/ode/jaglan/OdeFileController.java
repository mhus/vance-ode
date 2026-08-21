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

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

import de.mhus.vance.ode.inbound.OdeBadRequestException;
import de.mhus.vance.ode.inbound.OdeErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * The six endpoints Jaglan speaks, over one {@link FileSource}.
 *
 * <p>Everything here is boundary work — validate, check the declaration, hand
 * over. The source is never given a request it did not declare it could serve:
 * a write against a read-only source is refused here, not by the source's
 * default implementation.
 *
 * <p><b>Content is streamed, not marshalled.</b> {@code /content} answers with
 * the bytes and nothing else — no JSON envelope, no base64. A mount exists so
 * a large file can be read without a copy on either side, and an envelope
 * would put one on both.
 *
 * <p><b>404 versus 5xx is load-bearing here.</b> A missing file is a 404, and
 * the reader treats that as authoritative: it forgets its metadata row. A
 * source that fell over answers 5xx, and the reader keeps what it has. Getting
 * these the wrong way round means a brief outage tells somebody their document
 * does not exist — which is why {@link #stat} maps
 * {@link Optional#empty()} to 404 and every thrown exception to 500, and never
 * conflates them.
 */
@RestController
@RequestMapping("${vance.ode.jaglan.path:/ode/files}")
@RequiredArgsConstructor
@Slf4j
public class OdeFileController {

    private final FileSource source;
    private final VanceOdeJaglanProperties properties;

    /** What this source allows. Cacheable for as long as it says. */
    @GetMapping("/capabilities")
    public OdeFileCapabilities capabilities() {
        return source.capabilities();
    }

    /**
     * Metadata for one path.
     *
     * <p>404 when the source says it does not have it — an answer, not a
     * failure.
     */
    @GetMapping("/stat")
    public ResponseEntity<OdeFileEntry> stat(@RequestParam("path") String path) {
        Optional<OdeFileEntry> entry = source.stat(normalise(path));
        return entry.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Direct children of a folder, one level.
     *
     * <p>{@code path} may be omitted for the root. The result is authoritative
     * for that folder: what is absent here, the reader removes.
     */
    @GetMapping("/list")
    public List<OdeFileEntry> list(
            @RequestParam(value = "path", required = false) String path) {
        return source.list(normalise(path));
    }

    /**
     * The bytes.
     *
     * <p>Streamed through an {@link InputStreamResource} so neither side holds
     * the file. The mime type and length come from {@link FileSource#stat} —
     * one extra call, and worth it: without a {@code Content-Length} the reader
     * cannot show progress, and without a type it has to guess from the path.
     */
    @GetMapping("/content")
    public ResponseEntity<InputStreamResource> content(@RequestParam("path") String path) {
        String normalised = normalise(path);
        Optional<OdeFileEntry> entry = source.stat(normalised);
        if (entry.isEmpty() || entry.get().folder()) {
            return ResponseEntity.notFound().build();
        }
        OdeFileEntry meta = entry.get();
        Long limit = source.capabilities().maxBytes();
        if (limit != null && meta.size() > limit) {
            // Declared limit, enforced here rather than trusted: a source that
            // states a ceiling should not have to repeat the check.
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).build();
        }
        InputStream stream = source.open(normalised);
        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .contentType(meta.mimeType() == null
                        ? MediaType.APPLICATION_OCTET_STREAM
                        : MediaType.parseMediaType(meta.mimeType()));
        if (meta.size() > 0) {
            response.contentLength(meta.size());
        }
        if (meta.etag() != null) {
            response.header(HttpHeaders.ETAG, quoted(meta.etag()));
        }
        return response.body(new InputStreamResource(stream));
    }

    /**
     * Replace the bytes at a path.
     *
     * <p>Refused with 405 for a read-only source. Deliberately not 403: this is
     * a property of the source, not of who is asking, and a reader that sees
     * 403 would reasonably look for a credential problem.
     */
    @PutMapping("/content")
    public ResponseEntity<?> write(
            @RequestParam("path") String path, HttpServletRequest request) throws IOException {
        if (source.capabilities().access() != OdeFileAccess.READ_WRITE) {
            return refused("this file source is read-only");
        }
        OdeFileEntry written = source.write(normalise(path), request.getInputStream());
        return ResponseEntity.ok(written);
    }

    /** Delete at the source. Refused with 405 for a read-only source. */
    @DeleteMapping("/content")
    public ResponseEntity<?> delete(@RequestParam("path") String path) {
        if (source.capabilities().access() != OdeFileAccess.READ_WRITE) {
            return refused("this file source is read-only");
        }
        source.delete(normalise(path));
        return ResponseEntity.noContent().build();
    }

    /**
     * Search the source's own catalogue.
     *
     * <p>An undeclared search is an <b>empty list</b>, not a 404: a reader
     * holding a stale capabilities response should find the feature gone, not
     * the endpoint broken.
     */
    @GetMapping("/search")
    public List<OdeFileEntry> search(
            @RequestParam("q") String query,
            @RequestParam(value = "limit", required = false) Integer limit) {
        if (!source.capabilities().canSearch()) {
            return List.of();
        }
        if (query == null || query.isBlank()) {
            throw new OdeBadRequestException("q is required");
        }
        int clamped = Math.max(1, Math.min(
                limit == null ? properties.getDefaultSearchLimit() : limit,
                properties.getMaxSearchLimit()));
        return source.search(query.strip(), clamped);
    }

    // ── boundary helpers ─────────────────────────────────────────────

    /**
     * Normalise an inbound path to the form {@link OdeFileEntry} uses, and
     * refuse traversal.
     *
     * <p>Refusing {@code ..} here rather than resolving it means a source never
     * has to defend its own root: whatever a source does with the string it is
     * handed, it cannot be walked out of by this endpoint. The reader already
     * rejects traversal on its side — this is the second of the two, because
     * a public endpoint cannot rely on its callers being well-behaved.
     */
    private static String normalise(String raw) {
        if (raw == null) return "";
        String path = raw.strip();
        while (path.startsWith("/")) path = path.substring(1);
        while (path.endsWith("/")) path = path.substring(0, path.length() - 1);
        if (path.isEmpty()) return "";
        if (path.indexOf('\0') >= 0) {
            throw new OdeBadRequestException("path contains a NUL byte");
        }
        for (String segment : path.split("/")) {
            if (".".equals(segment) || "..".equals(segment)) {
                throw new OdeBadRequestException("path must not contain '.' or '..' segments");
            }
        }
        return path;
    }

    private static String quoted(String etag) {
        return etag.startsWith("\"") ? etag : '"' + etag + '"';
    }

    private static ResponseEntity<OdeErrorResponse> refused(String message) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(new OdeErrorResponse("read_only", message));
    }

    /**
     * A malformed request is the caller's problem and must not read as a source
     * failure — a reader backs off from 5xx, and backing off does not fix a
     * wrong path.
     */
    @ExceptionHandler(OdeBadRequestException.class)
    public ResponseEntity<OdeErrorResponse> onBadRequest(RuntimeException e) {
        return ResponseEntity.badRequest()
                .body(new OdeErrorResponse("bad_request", OdeBadRequestException.describe(e)));
    }

    /** A query parameter Spring could not convert, e.g. a non-numeric limit. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<OdeErrorResponse> onBadParameter(
            MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest().body(new OdeErrorResponse(
                "bad_request", "parameter '" + e.getName() + "' is not a valid value"));
    }

    /**
     * An operation the source declined. Distinct from a failure, and 405 like
     * the pre-checked refusals above so a reader classifies both the same way.
     */
    @ExceptionHandler(UnsupportedOperationException.class)
    public ResponseEntity<OdeErrorResponse> onUnsupported(UnsupportedOperationException e) {
        return refused(OdeBadRequestException.describe(e));
    }

    /**
     * Anything the source itself threw. 500, deliberately — the reader keeps
     * its cached metadata and retries later, which is the right response to
     * "cannot answer" and the wrong one to "does not exist".
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<OdeErrorResponse> onSourceFailure(RuntimeException e) {
        log.error("Jaglan files: the source failed to answer", e);
        return ResponseEntity.internalServerError()
                .body(new OdeErrorResponse("source_failed", OdeBadRequestException.describe(e)));
    }
}
