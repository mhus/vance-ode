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

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import de.mhus.vance.ode.inbound.OdeBadRequestException;
import de.mhus.vance.ode.inbound.OdeErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.InvalidMediaTypeException;
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
 *
 * <p><b>405 means "this source does not serve that shape of request".</b> Two
 * of them exist: a write against a read-only source ({@code read_only}) and a
 * parameterised read against a source that declared none
 * ({@code query_unsupported}). The reader treats a 405 as a stable refusal and
 * stops asking, so both are decided here from the declaration — and, where the
 * declaration and the implementation disagree, from an
 * {@link UnsupportedOperationException} caught inside {@link #write},
 * {@link #delete} and the parameterised branch of {@link #content} alone. It is
 * deliberately not a controller-wide handler: that exception is what every
 * immutable collection in the JDK throws, so one out of {@code list} or
 * {@code search} is an ordinary bug inside a source, and answering it with a
 * refusal would make a reader give up on a mount that is merely broken. The
 * same caveat applies to the three narrow catches — a source whose own computed
 * view trips over an immutable collection is read as a refusal — and it is
 * accepted for the same reason it already is on {@code write}: the alternative
 * is a permanent misconfiguration presenting as an outage and being retried
 * forever.
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
     *
     * <p><b>Any parameter other than {@code path} makes this a parameterised
     * read</b>, and a source that declared none refuses it. That is stricter
     * than ignoring the parameter, and deliberately so: ignoring is how a
     * caller ends up with the plain file believing it is the view they asked
     * for. The consequence is worth stating — a cache-buster or a stray
     * parameter appended by something in between now turns a working read into
     * a 405.
     *
     * <p>Only {@code path} is reserved, and only because it addresses the file.
     * The reader keeps its own reserved list for its own URL space
     * ({@code kind}, {@code download}) and strips those before forwarding;
     * that list is not repeated here, because the parameter namespace on this
     * endpoint belongs to the source, not to us.
     */
    @GetMapping("/content")
    public ResponseEntity<?> content(
            @RequestParam("path") String path, HttpServletRequest request) {
        String normalised = normalise(path);
        OdeQuery query = queryOf(request);
        Optional<OdeFileEntry> entry = source.stat(normalised);
        if (entry.isEmpty() || entry.get().folder()) {
            return ResponseEntity.notFound().build();
        }
        OdeFileEntry meta = entry.get();
        // One evaluation: FileSource#capabilities is documented as per-request
        // and cheap, and a source that took that at its word should not pay
        // twice on the most expensive path.
        OdeFileCapabilities caps = source.capabilities();
        boolean parameterised = !query.isEmpty();
        if (parameterised && !caps.supportsQuery()) {
            // 405 rather than 400: this is a property of the source, not of
            // the request. Through refused() like every other refusal, so it
            // carries Allow and a reason — a bare status leaves the reader
            // reporting a refusal it cannot explain.
            return refused("query_unsupported",
                    "this file source does not serve parameterised reads");
        }
        Long limit = caps.maxBytes();
        if (limit != null && !parameterised && meta.size() > limit) {
            // Declared limit, enforced here rather than trusted: a source that
            // states a ceiling should not have to repeat the check.
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).build();
        }
        // Resolved before the file is opened: an unparseable type is a
        // property of the metadata, and finding that out after open() would
        // leave the stream behind with nobody left to close it.
        //
        // It comes from stat even for a computed view, and that is a contract
        // rule rather than an oversight (see FileSource#open(String,OdeQuery)):
        // a view keeps the type of the path it is a view of. The reader renders
        // from the mime on its own metadata row, so a response that claimed a
        // different type would not survive the trip anyway.
        MediaType type = mediaTypeOf(meta.mimeType());
        InputStream stream;
        try {
            stream = parameterised
                    ? source.open(normalised, query)
                    : source.open(normalised);
        } catch (UnsupportedOperationException e) {
            // Declared supportsQuery, did not implement it. A permanent
            // refusal, not an outage: left as a 500 it would classify as
            // transient on the reader side and be retried forever against a
            // misconfiguration nobody is going to fix by waiting. Same
            // treatment, and the same caveat, as write and delete.
            if (!parameterised) throw e;
            return refused("query_unsupported", OdeBadRequestException.describe(e));
        }
        if (stream == null) {
            throw new IllegalStateException(
                    "the source returned no stream for '" + normalised + "'");
        }
        try {
            ResponseEntity.BodyBuilder response = ResponseEntity.ok().contentType(type);
            // Both of these describe the *plain* file and are wrong for a
            // computed view — a stale Content-Length truncates the answer or
            // breaks the response outright, and a stale ETag would let a
            // cache hand back one view in place of another.
            if (!parameterised && meta.size() > 0) {
                response.contentLength(meta.size());
            }
            if (!parameterised && meta.etag() != null) {
                response.header(HttpHeaders.ETAG, quoted(meta.etag()));
            }
            if (parameterised) {
                response.header(HttpHeaders.CACHE_CONTROL, "no-store");
                // The declared ceiling still applies — more so here than
                // anywhere else, because a computed view is the one answer
                // whose size nothing knows in advance. Without this the one
                // case that can produce arbitrary bytes would be the only one
                // running unbounded, and with Content-Length suppressed the
                // reader cannot pre-check either.
                stream = bounded(stream, limit, normalised);
            }
            return response.body(new InputStreamResource(stream));
        } catch (RuntimeException e) {
            // Only the converter closes what it writes, so anything thrown
            // between open() and handing the resource over loses the handle.
            // In a library embedded in somebody else's process that is their
            // file descriptors, not ours.
            close(stream);
            throw e;
        }
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
        InputStream body = request.getInputStream();
        OdeFileEntry written;
        try {
            written = source.write(normalise(path), body);
        } catch (UnsupportedOperationException e) {
            // The declaration and the implementation disagree — a refusal, and
            // caught here rather than controller-wide so that the same
            // exception thrown out of a read path stays a failure. See the
            // note on the class.
            return refused(OdeBadRequestException.describe(e));
        }
        return ResponseEntity.ok(written);
    }

    /** Delete at the source. Refused with 405 for a read-only source. */
    @DeleteMapping("/content")
    public ResponseEntity<?> delete(@RequestParam("path") String path) {
        if (source.capabilities().access() != OdeFileAccess.READ_WRITE) {
            return refused("this file source is read-only");
        }
        try {
            source.delete(normalise(path));
        } catch (UnsupportedOperationException e) {
            return refused(OdeBadRequestException.describe(e));
        }
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
        int wanted = limit != null ? limit : properties.getDefaultSearchLimit();
        if (wanted <= 0) {
            // Neither the caller nor the operator named a usable number.
            wanted = VanceOdeJaglanProperties.DEFAULT_SEARCH_LIMIT;
        }
        int clamped = Math.min(wanted, positiveOr(properties.getMaxSearchLimit()));
        return source.search(query.strip(), clamped);
    }

    // ── boundary helpers ─────────────────────────────────────────────

    /**
     * A configured ceiling of zero or less read as "unset", not as "serve one
     * row" — the same rule Centauri's {@code clampLimit} applies, and for the
     * same reason: taken literally, a typo in the configuration would look like
     * a source that only ever has a single match.
     */
    private static int positiveOr(int configured) {
        return configured > 0 ? configured : Integer.MAX_VALUE;
    }

    /**
     * The declared type, or octet-stream when it is not a media type.
     *
     * <p>A source may legitimately have an empty mime column, and
     * {@code parseMediaType} throws for anything without a {@code type/subtype}
     * shape. Falling back costs nothing — the reader guesses from the extension
     * anyway — while throwing would make a typo in somebody's data look like an
     * outage.
     */
    /**
     * The request's parameters, minus the one that addresses the file.
     *
     * <p>{@code path} is this endpoint's own and never a source parameter — a
     * source that saw it would be reading the address as data. It is dropped
     * here rather than refused, because the reader already refuses a query
     * that declares it; by the time a request arrives, a {@code path} is ours.
     */
    private static OdeQuery queryOf(HttpServletRequest request) {
        Map<String, List<String>> parameters = new LinkedHashMap<>();
        request.getParameterMap().forEach((name, values) -> {
            if (!"path".equals(name) && values != null && values.length > 0) {
                parameters.put(name, List.of(values));
            }
        });
        return parameters.isEmpty() ? OdeQuery.EMPTY : new OdeQuery(parameters);
    }

    private static MediaType mediaTypeOf(@Nullable String declared) {
        if (declared == null || declared.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(declared);
        } catch (InvalidMediaTypeException e) {
            log.debug("Jaglan files: '{}' is not a media type; serving as octet-stream", declared);
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    /** For a stream nobody will be handed, so nobody else will close it. */
    private static void close(InputStream stream) {
        try {
            stream.close();
        } catch (IOException e) {
            log.debug("Jaglan files: failed to close a stream that was never served", e);
        }
    }

    /**
     * Normalise an inbound path to the form {@link OdeFileEntry} uses, and
     * refuse traversal.
     *
     * <p>Refusing {@code ..} here rather than resolving it means a source never
     * has to defend its own root: whatever a source does with the string it is
     * handed, it cannot be walked out of by this endpoint. The reader already
     * rejects traversal on its side — this is the second of the two, because
     * a public endpoint cannot rely on its callers being well-behaved.
     *
     * <p>Which is why {@code /} is not the only separator considered. The
     * contract says paths use {@code /}, but a source is free to hand the
     * string to {@code Path.resolve} on whatever platform it runs on, and there
     * {@code ..\..\etc} is one segment that walks out and {@code C:/x} replaces
     * the base outright. Both are refused here, so the assurance above holds
     * for a source that did nothing but resolve against its own root.
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
        if (path.indexOf('\\') >= 0) {
            // Not a separator in this contract, and a separator on Windows —
            // which is exactly why it cannot be let through as an ordinary
            // character. OdeKitBundle refuses it for the same reason.
            throw new OdeBadRequestException("path must use '/' and must not contain '\\'");
        }
        for (String segment : path.split("/")) {
            if (".".equals(segment) || "..".equals(segment)) {
                throw new OdeBadRequestException("path must not contain '.' or '..' segments");
            }
            if (segment.isEmpty()) {
                throw new OdeBadRequestException("path must not contain empty segments");
            }
            if (hasDriveLetter(segment)) {
                throw new OdeBadRequestException(
                        "path must be relative and must not name a drive");
            }
        }
        return path;
    }

    /** {@code C:} and anything like it — absolute on Windows, and no {@code ..} needed. */
    private static boolean hasDriveLetter(String segment) {
        return segment.length() >= 2
                && segment.charAt(1) == ':'
                && Character.isLetter(segment.charAt(0));
    }

    private static String quoted(String etag) {
        return etag.startsWith("\"") ? etag : '"' + etag + '"';
    }

    /**
     * A refusal that is a property of the source, not of who is asking.
     *
     * <p>{@code Allow} because RFC 9110 requires it on a 405. The reader only
     * reads the status, but a proxy or a browser between the two does not.
     */
    private static ResponseEntity<OdeErrorResponse> refused(String message) {
        return refused("read_only", message);
    }

    /**
     * The same refusal with an explicit code.
     *
     * <p>Two shapes of request a source can decline — writing to a read-only
     * source, and a parameterised read it does not serve — and they are not
     * the same fact. One code for both would tell a reader "read_only" about a
     * source that is perfectly writable.
     */
    private static ResponseEntity<OdeErrorResponse> refused(String code, String message) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .header(HttpHeaders.ALLOW, "GET")
                .body(new OdeErrorResponse(code, message));
    }

    /**
     * Cap a stream at the source's declared {@code maxBytes}.
     *
     * <p>It <b>fails</b> past the limit rather than truncating: a short answer
     * that arrives with a 200 is indistinguishable from a complete one, and
     * for computed content — where nobody knows the expected length — nothing
     * downstream could catch it. The transfer breaking is ugly and visible,
     * which is the right way round.
     *
     * <p>There is no way to turn this into a 413: the status is on the wire
     * before the first byte is produced. A source that exceeds its own declared
     * ceiling is broken, and this is the containment, not the diagnosis.
     */
    private static InputStream bounded(InputStream source, @Nullable Long limit, String path) {
        if (limit == null || limit <= 0) {
            return source;
        }
        long max = limit;
        return new FilterInputStream(source) {
            private long read;

            @Override
            public int read() throws IOException {
                int b = super.read();
                if (b != -1) count(1);
                return b;
            }

            @Override
            public int read(byte[] buffer, int off, int len) throws IOException {
                int n = super.read(buffer, off, len);
                if (n > 0) count(n);
                return n;
            }

            private void count(int n) throws IOException {
                read += n;
                if (read > max) {
                    log.error("Jaglan files: computed view of '{}' exceeded the declared "
                            + "maxBytes of {} — aborting the transfer", path, max);
                    throw new IOException("computed content exceeded the declared maxBytes of "
                            + max + " for '" + path + "'");
                }
            }
        };
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
