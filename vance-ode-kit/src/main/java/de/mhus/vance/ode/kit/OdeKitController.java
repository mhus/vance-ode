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

    private final SortedMap<String, KitSource> byId;
    private final VanceOdeKitProperties properties;

    public OdeKitController(List<KitSource> sources, VanceOdeKitProperties properties) {
        this.properties = properties;
        // Sorted by id, not bean-discovery order: that order is not stable
        // between runs, and it decides the order of the capabilities list.
        SortedMap<String, KitSource> map = new TreeMap<>();
        for (KitSource source : sources) {
            String id = source.declare().id();
            KitSource clash = map.put(id, source);
            if (clash != null) {
                // Refused at startup rather than served by whichever bean the
                // context happened to order last. Two sources claiming one id
                // means one of them is silently unreachable.
                throw new IllegalStateException("two kit sources both declare id '" + id
                        + "': " + clash.getClass().getName() + " and "
                        + source.getClass().getName());
            }
        }
        this.byId = java.util.Collections.unmodifiableSortedMap(map);
        log.info("Ode kit endpoint serving {} kit(s): {}", byId.size(), byId.keySet());
    }

    /**
     * What is on offer, without building any of it.
     *
     * <p>An empty list is a normal answer — see {@link OdeKitCapabilities}.
     */
    @GetMapping("/capabilities")
    public OdeKitCapabilities capabilities() {
        List<OdeKitDeclaration> declarations = new ArrayList<>(byId.size());
        for (KitSource source : byId.values()) {
            declarations.add(source.declare());
        }
        return new OdeKitCapabilities(declarations);
    }

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
        KitSource source = byId.get(request.kit());
        if (source == null) {
            throw new OdeBadRequestException("this application does not serve kit='"
                    + request.kit() + "'; it serves " + byId.keySet());
        }

        log.debug("Building kit '{}' for {}/{} of instance '{}'",
                request.kit(), request.tenant(), request.project(), request.instance());

        byte[] archive = pack(source.build(request), request.kit());
        if (archive.length > properties.getMaxBundleBytes()) {
            throw new IllegalStateException("kit '" + request.kit() + "' packs to "
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
}
