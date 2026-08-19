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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Rejects requests to an inbound endpoint without the configured shared secret.
 *
 * <p>An interceptor rather than a check inside each handler: N call sites would
 * be N chances to forget the N+1st. Registered only when
 * {@link OdeInboundEndpoint#isSecured()}.
 */
public final class OdeApiKeyInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(OdeApiKeyInterceptor.class);

    private static final String BEARER = "Bearer ";

    private final OdeInboundEndpoint endpoint;

    public OdeApiKeyInterceptor(OdeInboundEndpoint endpoint) {
        this.endpoint = endpoint;
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!endpoint.isSecured()) {
            return true;
        }
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER)
                && matches(header.substring(BEARER.length()).trim())) {
            return true;
        }
        log.debug("Ode inbound: rejected {} {} — missing or wrong bearer token",
                request.getMethod(), request.getRequestURI());
        response.setStatus(401);
        return false;
    }

    /** Constant-time comparison — a shared secret should not leak by timing. */
    private boolean matches(String presented) {
        return MessageDigest.isEqual(
                presented.getBytes(StandardCharsets.UTF_8),
                endpoint.getApiKey().getBytes(StandardCharsets.UTF_8));
    }
}
