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

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Rejects feed requests without the configured shared secret.
 *
 * <p>An interceptor rather than a check inside each handler: five call sites
 * would be five chances to forget the sixth. Only registered when
 * {@link VanceOdeCentauriProperties#isSecured()} — an unset key means the
 * endpoint is open, on the assumption that the surrounding application may
 * already guard the path and does not need a second scheme fighting its own.
 */
@RequiredArgsConstructor
@Slf4j
public class OdeFeedApiKeyInterceptor implements HandlerInterceptor {

    private static final String BEARER = "Bearer ";

    private final VanceOdeCentauriProperties properties;

    @Override
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!properties.isSecured()) {
            return true;
        }
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER)
                && matches(header.substring(BEARER.length()).trim())) {
            return true;
        }
        log.debug("Centauri feed: rejected {} {} — missing or wrong bearer token",
                request.getMethod(), request.getRequestURI());
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        return false;
    }

    /** Constant-time comparison — a shared secret should not leak by timing. */
    private boolean matches(String presented) {
        return MessageDigest.isEqual(
                presented.getBytes(StandardCharsets.UTF_8),
                properties.getApiKey().getBytes(StandardCharsets.UTF_8));
    }
}
