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
package de.mhus.vance.ode.centauri;

import de.mhus.vance.ode.inbound.OdeErrorResponse;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The five endpoints Centauri speaks, over one {@link FeedSource}.
 *
 * <p>Everything here is boundary work — validate, clamp, translate, hand over.
 * The source is never given a request it did not declare it could serve, which
 * is why capabilities are consulted before every call rather than trusted
 * afterwards.
 *
 * <p><b>Wire forms.</b> Timestamps are ISO-8601 instants
 * ({@code 2026-08-19T09:30:00Z}) and the capabilities TTL is an ISO-8601
 * duration ({@code PT30M}) — both self-describing, which matters more in a
 * contract between two systems than brevity does.
 *
 * <p><b>The reader pseudonym</b> ({@link OdeFeedHeaders#READER}) is read on the
 * three entry-facing calls and nowhere else. Capabilities and selectors
 * describe the source, not the person asking, so they stay cacheable across all
 * readers.
 */
@RestController
@RequestMapping("${vance.ode.centauri.path:/ode/feed}")
@RequiredArgsConstructor
@Slf4j
public class OdeFeedController {

    private final FeedSource source;
    private final VanceOdeCentauriProperties properties;

    /** What this source can do. Reader-independent and cacheable. */
    @GetMapping("/capabilities")
    public OdeCapabilities capabilities() {
        return source.capabilities();
    }

    /** The finite selector list, empty for free-form and single-stream sources. */
    @GetMapping("/selectors")
    public List<OdeSelector> selectors() {
        return source.selectors();
    }

    /**
     * One page of one stream.
     *
     * <p>Three guards run before the source sees anything: a direction it
     * cannot serve is refused, a free-form selector it rejects is refused, and
     * the limit is clamped to the smaller of what it can serve and what the
     * operator allows. Filters are narrowed to the declared pushdowns, so a
     * source is never handed a filter it would silently ignore — the caller
     * applies the rest itself.
     */
    @GetMapping("/items")
    public OdeItemPage items(
            @RequestParam(defaultValue = "") String selector,
            @RequestParam(required = false) @Nullable String cursor,
            @RequestParam(defaultValue = "OLDER") OdeDirection direction,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) @Nullable String text,
            @RequestParam(required = false) @Nullable List<String> languages,
            @RequestParam(required = false) @Nullable String since,
            @RequestHeader(name = OdeFeedHeaders.READER, required = false)
            @Nullable String reader) {

        OdeCapabilities caps = source.capabilities();
        if (direction == OdeDirection.NEWER && !caps.supportsNewerDirection()) {
            throw new IllegalArgumentException(
                    "this source does not serve direction=NEWER");
        }
        if (caps.selectorMode() == OdeSelectorMode.FREEFORM) {
            Optional<String> complaint = source.validateSelector(selector);
            if (complaint.isPresent()) {
                throw new IllegalArgumentException(complaint.get());
            }
        }

        OdeItemQuery query = new OdeItemQuery(
                selector,
                cursor,
                direction,
                clampLimit(limit, caps),
                caps.pushdownTextSearch() ? text : null,
                caps.pushdownLanguage() ? normalizeLanguages(languages) : Set.of(),
                caps.pushdownSince() ? parseSince(since) : null,
                reader);

        OdeItemPage page = source.items(query);
        warnIfOutOfOrder(page, direction);
        return page;
    }

    /**
     * The full text of one entry. 404 for an entry this source does not know —
     * an entry can legitimately have aged out of the stream between the page
     * and the click.
     */
    @GetMapping("/item/{itemId}")
    public ResponseEntity<OdeItemBody> item(
            @PathVariable String itemId,
            @RequestHeader(name = OdeFeedHeaders.READER, required = false)
            @Nullable String reader) {
        return source.body(itemId, reader)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * A back-channel signal about one entry.
     *
     * <p>{@code 202} when taken, {@code 501} when this source does not accept
     * that signal, {@code 409} when it refuses this particular one. The
     * capability is checked here, so a source that declares an empty set never
     * needs to implement a refusal.
     */
    @PostMapping("/signal")
    public ResponseEntity<OdeSignalResponse> signal(
            @RequestBody OdeSignalRequest request,
            @RequestHeader(name = OdeFeedHeaders.READER, required = false)
            @Nullable String reader) {

        OdeCapabilities caps = source.capabilities();
        if (!caps.signalsAccepted().contains(request.signal())) {
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                    .body(new OdeSignalResponse(OdeSignalOutcome.UNSUPPORTED,
                            "this source does not accept " + request.signal()));
        }

        OdeSignalResponse response = source.signal(request.withReader(reader));
        HttpStatus status = switch (response.outcome()) {
            case ACCEPTED -> HttpStatus.ACCEPTED;
            case UNSUPPORTED -> HttpStatus.NOT_IMPLEMENTED;
            case REJECTED -> HttpStatus.CONFLICT;
        };
        return ResponseEntity.status(status).body(response);
    }

    /**
     * A malformed request is the caller's problem and must not read as a
     * source failure — the caller backs off from 5xx, and a wrong parameter is
     * not something backing off would fix.
     */
    @ExceptionHandler({IllegalArgumentException.class, DateTimeParseException.class})
    public ResponseEntity<OdeErrorResponse> onBadRequest(RuntimeException e) {
        return ResponseEntity.badRequest()
                .body(new OdeErrorResponse("bad_request", String.valueOf(e.getMessage())));
    }

    // ── internals ────────────────────────────────────────────────────

    /**
     * Two ceilings for two different reasons: the capability figure is what the
     * source can serve, the property is what the operator lets one request
     * cost.
     */
    private int clampLimit(int requested, OdeCapabilities caps) {
        if (requested <= 0) {
            throw new IllegalArgumentException("limit must be > 0, was " + requested);
        }
        return Math.min(requested, Math.min(caps.maxPageSize(), properties.getMaxLimit()));
    }

    private static Set<String> normalizeLanguages(@Nullable List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return Set.of();
        }
        Set<String> out = new LinkedHashSet<>();
        for (String entry : raw) {
            if (entry == null) {
                continue;
            }
            for (String part : entry.split(",")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    out.add(trimmed);
                }
            }
        }
        return Set.copyOf(out);
    }

    private static @Nullable Instant parseSince(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(raw.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "since must be an ISO-8601 instant, was '" + raw + "'");
        }
    }

    /**
     * A page in the wrong order is not rejected — the caller re-sorts and only
     * loses ordering quality — but it is reported here, where the person who
     * can fix it is reading the log. Silence would leave a contract violation
     * to be discovered as odd behaviour in someone else's UI.
     */
    private static void warnIfOutOfOrder(OdeItemPage page, OdeDirection direction) {
        List<OdeItem> items = page.items();
        for (int i = 1; i < items.size(); i++) {
            Instant previous = items.get(i - 1).publishedAt();
            Instant current = items.get(i).publishedAt();
            boolean ordered = direction == OdeDirection.NEWER
                    ? !current.isBefore(previous)
                    : !current.isAfter(previous);
            if (!ordered) {
                log.warn("Centauri feed: page is not ordered by publishedAt {} — "
                                + "'{}' ({}) follows '{}' ({}). The caller will re-sort, but the "
                                + "cursor is derived from this order, so fix it at the source.",
                        direction == OdeDirection.NEWER ? "ascending" : "descending",
                        items.get(i).id(), current, items.get(i - 1).id(), previous);
                return;
            }
        }
    }
}
