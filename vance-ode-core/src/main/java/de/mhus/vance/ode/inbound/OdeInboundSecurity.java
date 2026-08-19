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

    public static WebMvcConfigurer guarding(OdeInboundEndpoint endpoint) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                if (!endpoint.isSecured()) {
                    return;
                }
                registry.addInterceptor(new OdeApiKeyInterceptor(endpoint))
                        .addPathPatterns(endpoint.getPath() + "/**");
            }
        };
    }
}
