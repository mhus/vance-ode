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

import de.mhus.vance.ode.inbound.OdeBadRequestException;
import de.mhus.vance.ode.inbound.OdeErrorResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The two endpoints Vancetope's kit provisioning speaks, over the published
 * {@link KitSource} beans.
 *
 * <p>Everything here is boundary work — route, validate, pack, hand over. The
 * split between the endpoints is the point of the contract: {@code capabilities}
 * is what a reader asks on a schedule to find out whether anything changed, and
 * it must not build anything; {@code build} is what it asks when the answer was
 * yes.
 *
 * <p><b>Build is a POST</b> although it changes nothing here, because the
 * request carries a structured body — and because a url is where caches and
 * access logs keep things, which is the wrong place for the name of a tenant.
 */
@RestController
@RequestMapping("${vance.ode.kit.path:/kit}")
@Slf4j
public class OdeKitController {

    private final List<KitSource> sources;
    private final VanceOdeKitProperties properties;

    public OdeKitController(List<KitSource> sources, VanceOdeKitProperties properties) {
        this.properties = properties;
        this.sources = List.copyOf(sources);
        // Routing is resolved per request rather than held from here, because
        // declare() is allowed to fail: a directory the operator creates on
        // first deploy, an index that is not up yet. This module is embedded in
        // software it does not own, and refusing to build a bean would stop
        // that application from starting over a feature it merely offers.
        //
        // What can be answered at startup still is, because it is the more
        // useful moment to hear it: two sources claiming one id means one of
        // them is silently unreachable, and that is worth refusing over. A
        // source that could not be asked is logged and left to the request
        // path, where it gets another chance.
        Map<String, String> declared = new TreeMap<>();
        for (KitSource source : this.sources) {
            String id;
            try {
                id = source.declare().id();
            } catch (RuntimeException e) {
                log.error("Kit source {} could not declare itself; it is left out until it can",
                        source.getClass().getName(), e);
                continue;
            }
            String clash = declared.put(id, source.getClass().getName());
            if (clash != null) {
                throw new IllegalStateException("two kit sources both declare id '" + id
                        + "': " + clash + " and " + source.getClass().getName());
            }
        }
        log.info("Ode kit endpoint serving {} kit(s): {}", declared.size(), declared.keySet());
    }

    /**
     * What is on offer, without building any of it.
     *
     * <p>An empty list is a normal answer — see {@link OdeKitCapabilities}.
     */
    @GetMapping("/capabilities")
    public OdeKitCapabilities capabilities() {
        return new OdeKitCapabilities(resolve().declarations());
    }

    /**
     * Ask every source what it is offering, right now.
     *
     * <p>Sorted by id, not by bean-discovery order: that order is not stable
     * between runs, and it decides the order of the capabilities list.
     *
     * <p>A source that throws is left out and counted rather than propagated.
     * {@code declare()} is contractually cheap, so asking per request costs
     * what {@code capabilities} already cost, and it buys the two things a
     * startup snapshot cannot: a source that becomes available later shows up,
     * and one that breaks later disappears instead of being served stale.
     */
    private Resolution resolve() {
        SortedMap<String, Declared> byId = new TreeMap<>();
        int unavailable = 0;
        for (KitSource source : sources) {
            OdeKitDeclaration declaration;
            try {
                declaration = source.declare();
            } catch (RuntimeException e) {
                log.error("Kit source {} failed to declare itself; leaving it out of this answer",
                        source.getClass().getName(), e);
                unavailable++;
                continue;
            }
            Declared clash = byId.putIfAbsent(declaration.id(), new Declared(declaration, source));
            if (clash != null) {
                // Refused at startup when both sources could be asked then; if
                // one only became declarable later there is nothing left to
                // refuse, so the first by bean order is served and the loss is
                // named rather than silent.
                log.error("Kit sources {} and {} both declare id '{}'; serving the first",
                        clash.source().getClass().getName(), source.getClass().getName(),
                        declaration.id());
            }
        }
        List<OdeKitDeclaration> declarations = new ArrayList<>(byId.size());
        for (Declared declared : byId.values()) {
            declarations.add(declared.declaration());
        }
        return new Resolution(byId, declarations, unavailable);
    }

    /** One source and what it just said about itself. */
    private record Declared(OdeKitDeclaration declaration, KitSource source) {}

    /** What the sources say right now, and how many of them could not be asked. */
    private record Resolution(
            SortedMap<String, Declared> byId,
            List<OdeKitDeclaration> declarations,
            int unavailable) {}

    /**
     * Build one kit and hand it over as a zip.
     *
     * <p>An unknown kit is a 400, not a 404: the caller named something this
     * application does not serve, and it is expected to back off from a server
     * error but to fix a request it got wrong.
     */
    @PostMapping("/build")
    public ResponseEntity<byte[]> build(@RequestBody OdeKitBuildRequest request) {
        if (request == null || request.kit() == null || request.kit().isBlank()) {
            throw new OdeBadRequestException("kit is required");
        }
        if (request.tenant() == null || request.tenant().isBlank()) {
            // Not used for anything here by default, but a request that cannot
            // say where it is going cannot be reasoned about in a log either,
            // and that is the reason the field exists.
            throw new OdeBadRequestException("tenant is required");
        }
        Resolution resolution = resolve();
        Declared declared = resolution.byId().get(request.kit());
        if (declared == null) {
            if (resolution.unavailable() > 0) {
                // "This application does not serve that" is a 400 the caller is
                // expected to act on, and we cannot honestly say it while a
                // source that might have served it could not be asked. 5xx
                // instead, which is the answer a reader backs off from.
                throw new IllegalStateException("kit '" + request.kit() + "' is not among "
                        + resolution.byId().keySet() + ", but " + resolution.unavailable()
                        + " kit source(s) could not be asked");
            }
            throw new OdeBadRequestException("this application does not serve kit='"
                    + request.kit() + "'; it serves " + resolution.byId().keySet());
        }

        log.debug("Building kit '{}' for {}/{} of instance '{}'",
                request.kit(), request.tenant(), request.project(), request.instance());

        byte[] archive = pack(declared.source().build(request), request.kit());
        if (archive.length > properties.getMaxBundleBytes()) {
            throw new BundleTooLargeException("kit '" + request.kit() + "' packs to "
                    + archive.length + " bytes, over the configured limit of "
                    + properties.getMaxBundleBytes());
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + request.kit() + ".zip\"")
                .body(archive);
    }

    /**
     * Pack the bundle, entries sorted and without timestamps.
     *
     * <p>Both on purpose: the same bundle packed twice produces the same bytes,
     * so a caller that hashes what it received sees „unchanged" when nothing
     * changed. The revision travels separately, but an archive that differs on
     * every request is confusing to anyone comparing downloads by hand.
     */
    private static byte[] pack(OdeKitBundle bundle, String kitId) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (Map.Entry<String, byte[]> e : new TreeMap<>(bundle.files()).entrySet()) {
                ZipEntry entry = new ZipEntry(e.getKey());
                entry.setTime(0L);
                zip.putNextEntry(entry);
                zip.write(e.getValue());
                zip.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to pack kit '" + kitId + "'", e);
        }
        return out.toByteArray();
    }

    /** A kit that packs to more than the operator allows over the wire. */
    static final class BundleTooLargeException extends RuntimeException {
        BundleTooLargeException(String message) {
            super(message);
        }
    }

    @ExceptionHandler(OdeBadRequestException.class)
    public ResponseEntity<OdeErrorResponse> onBadRequest(OdeBadRequestException e) {
        return ResponseEntity.badRequest()
                .body(new OdeErrorResponse("bad_request", e.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<OdeErrorResponse> onUnreadableBody(HttpMessageNotReadableException e) {
        log.debug("Unreadable kit build body", e);
        return ResponseEntity.badRequest()
                .body(new OdeErrorResponse("bad_request", "request body is not valid JSON"));
    }

    /**
     * A kit over the size limit. 413, not 500 — waiting changes nothing about
     * it, and a reader that reads it as an outage retries a request that cannot
     * begin to succeed until somebody edits either the kit or the limit.
     */
    @ExceptionHandler(BundleTooLargeException.class)
    public ResponseEntity<OdeErrorResponse> onBundleTooLarge(BundleTooLargeException e) {
        log.error("Ode kit: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(new OdeErrorResponse("bundle_too_large", e.getMessage()));
    }

    /**
     * Anything a source itself threw. 500 with a body of our own, deliberately:
     * without this the exception escapes into whatever the host application
     * does with unhandled ones, and a host that answers 200 with its own error
     * page would have the reader unpack it as a zip. The cause is also logged
     * here, which is the only place that knows which source it was.
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<OdeErrorResponse> onSourceFailure(RuntimeException e) {
        log.error("Ode kit: the source failed to answer", e);
        return ResponseEntity.internalServerError()
                .body(new OdeErrorResponse("source_failed", OdeBadRequestException.describe(e)));
    }
}
