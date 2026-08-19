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

import org.jspecify.annotations.Nullable;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * One line for an inbound auto-configuration to guard its own path.
 *
 * <p>Path-scoped on purpose: an application that guards its endpoints itself is
 * untouched everywhere else.
 */
public final class OdeInboundSecurity {

    private OdeInboundSecurity() {
        /* factory only */
    }

    /** Guards on the configured shared secret, if one is set. */
    public static WebMvcConfigurer guarding(OdeInboundEndpoint endpoint) {
        return guarding(endpoint, null);
    }

    /**
     * Guards on the application's own {@link OdeAuthService} when it published
     * one, on the configured shared secret otherwise.
     *
     * <p>Note which of the two decides whether the guard is registered at all:
     * an auth service secures the path <b>regardless</b> of {@code api-key}. An
     * application that took the trouble to write a validator has said what it
     * wants, and reading an unset property as "leave it open" would be the one
     * failure mode that opens access rather than closing it.
     */
    public static WebMvcConfigurer guarding(
            OdeInboundEndpoint endpoint, @Nullable OdeAuthService authService) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                if (authService == null && !endpoint.isSecured()) {
                    return;
                }
                // normalisedPath(), never getPath(): a configured trailing slash
                // would otherwise build "/ode/feed//**", which matches nothing
                // while the endpoints stay mapped — a guard that fails open.
                String base = endpoint.normalisedPath();
                registry.addInterceptor(new OdeAuthInterceptor(endpoint, authService))
                        .addPathPatterns(base, base + "/**");
            }
        };
    }
}
