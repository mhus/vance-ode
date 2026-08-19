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
package de.mhus.vance.ode.inbound;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Rejects requests to an inbound endpoint that do not carry an acceptable
 * bearer token.
 *
 * <p>An interceptor rather than a check inside each handler: N call sites would
 * be N chances to forget the N+1st.
 *
 * <p><b>Two ways to be acceptable, and only ever one of them at a time.</b> With
 * an {@link OdeAuthService} bean the application decides and names the caller;
 * without one the token is compared against the configured shared secret. When
 * both are present the service wins and the property is ignored — see
 * {@link OdeAuthService} for why they are not combined.
 */
public final class OdeAuthInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(OdeAuthInterceptor.class);

    private static final String BEARER = "Bearer ";

    private final OdeInboundEndpoint endpoint;
    private final @Nullable OdeAuthService authService;

    /** Static shared secret only. */
    public OdeAuthInterceptor(OdeInboundEndpoint endpoint) {
        this(endpoint, null);
    }

    public OdeAuthInterceptor(OdeInboundEndpoint endpoint, @Nullable OdeAuthService authService) {
        this.endpoint = endpoint;
        this.authService = authService;
        if (authService != null && endpoint.isSecured()) {
            log.warn("Ode inbound {}: an OdeAuthService is present, so the configured "
                            + "api-key is ignored. Remove the property to say so in the "
                            + "configuration too.",
                    endpoint.normalisedPath());
        }
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (authService != null) {
            return authenticate(request, response);
        }
        if (!endpoint.isSecured()) {
            return true;
        }
        String presented = bearer(request);
        if (presented != null && matchesSharedSecret(presented)) {
            return true;
        }
        return reject(request, response, 401, "missing or wrong bearer token");
    }

    /**
     * The delegating path.
     *
     * <p>A missing header never reaches the service: deciding that no token at
     * all is acceptable would be a decision about whether this endpoint is
     * public, and that is answered by publishing the bean or not — not by a
     * token validator asked to rule on the absence of a token.
     */
    private boolean authenticate(HttpServletRequest request, HttpServletResponse response) {
        String presented = bearer(request);
        if (presented == null) {
            return reject(request, response, 401, "no bearer token");
        }
        OdeAuthDecision decision;
        try {
            decision = authService.authenticate(presented, endpoint.normalisedPath());
        } catch (RuntimeException e) {
            // Fail closed. A validator whose backing store is unreachable must
            // not become an open door, and the caller can retry.
            log.warn("Ode inbound {}: auth service threw, refusing the request",
                    endpoint.normalisedPath(), e);
            return reject(request, response, 401, "auth service failed");
        }
        if (decision == null) {
            log.warn("Ode inbound {}: auth service returned null, refusing the request",
                    endpoint.normalisedPath());
            return reject(request, response, 401, "auth service returned no decision");
        }
        return switch (decision.outcome()) {
            case ALLOW -> {
                request.setAttribute(OdeCaller.ATTRIBUTE, decision.caller());
                yield true;
            }
            case FORBIDDEN -> reject(request, response, 403, message(decision, "forbidden"));
            case UNAUTHENTICATED ->
                    reject(request, response, 401, message(decision, "token not accepted"));
        };
    }

    private static String message(OdeAuthDecision decision, String fallback) {
        return decision.message() == null || decision.message().isBlank()
                ? fallback
                : decision.message();
    }

    private static @Nullable String bearer(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER)) {
            return null;
        }
        String token = header.substring(BEARER.length()).trim();
        return token.isEmpty() ? null : token;
    }

    /** Constant-time comparison — a shared secret should not leak by timing. */
    private boolean matchesSharedSecret(String presented) {
        return MessageDigest.isEqual(
                presented.getBytes(StandardCharsets.UTF_8),
                endpoint.getApiKey().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * The reason goes to the log and never into the response: the party being
     * refused is the last one that should be told which half of its credential
     * was wrong.
     */
    private boolean reject(
            HttpServletRequest request, HttpServletResponse response, int status, String reason) {
        log.debug("Ode inbound: rejected {} {} — {}",
                request.getMethod(), request.getRequestURI(), reason);
        if (status == 401) {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        }
        response.setStatus(status);
        return false;
    }
}
