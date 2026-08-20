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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.handler.MappedInterceptor;
import org.springframework.web.util.ServletRequestPathUtils;

/**
 * Whether the guard is registered at all.
 *
 * <p>The failure this protects against opens access rather than closing it: an
 * application that publishes an {@link OdeAuthService} but leaves
 * {@code api-key} unset used to get no interceptor and therefore no check, with
 * the endpoints happily mapped.
 */
class OdeInboundSecurityTest {

    private static final OdeAuthService ALLOW_ALL =
            (token, path) -> OdeAuthDecision.allow(OdeCaller.of("acme"));

    @Test
    void noSecretAndNoService_registersNothing() {
        assertThat(interceptorsOf(endpoint(""), null)).isEmpty();
    }

    @Test
    void secretOnly_registersTheGuard() {
        assertThat(interceptorsOf(endpoint("s3cret"), null)).hasSize(1);
    }

    @Test
    void serviceWithoutSecret_registersTheGuard() {
        assertThat(interceptorsOf(endpoint(""), ALLOW_ALL)).hasSize(1);
    }

    @Test
    void configuredPath_guardsTheEndpointsBelowIt() {
        assertThat(guards("/ode/search", "/ode/search/search")).isTrue();
        assertThat(guards("/ode/search", "/ode/search/capabilities")).isTrue();
        assertThat(guards("/ode/search", "/something/else")).isFalse();
    }

    @Test
    void rootPath_guardsTheEndpointsItStillMaps() {
        // The endpoints are mapped at /search and /capabilities when the path is
        // empty. The patterns used to be built from "/" — "/" and "//**" — which
        // match neither, leaving the guard silent over a served endpoint.
        assertThat(guards("", "/search")).isTrue();
        assertThat(guards("/", "/capabilities")).isTrue();
    }

    /** Whether a request for {@code requestUri} would hit the registered guard. */
    private static boolean guards(String configuredPath, String requestUri) {
        List<Object> interceptors = interceptorsOf(endpoint(configuredPath, "s3cret"), null);
        assertThat(interceptors).hasSize(1);
        MappedInterceptor mapped = (MappedInterceptor) interceptors.get(0);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", requestUri);
        ServletRequestPathUtils.parseAndCache(request);
        return mapped.matches(request);
    }

    private static List<Object> interceptorsOf(
            OdeInboundEndpoint endpoint, OdeAuthService authService) {
        ProbeRegistry registry = new ProbeRegistry();
        OdeInboundSecurity.guarding(endpoint, authService).addInterceptors(registry);
        return registry.registered();
    }

    private static OdeInboundEndpoint endpoint(String apiKey) {
        return endpoint("/ode/search", apiKey);
    }

    private static OdeInboundEndpoint endpoint(String path, String apiKey) {
        return new OdeInboundEndpoint() {

            @Override
            public String getPath() {
                return path;
            }

            @Override
            public String getApiKey() {
                return apiKey;
            }
        };
    }

    /** Reading back what was registered — the accessor is protected. */
    private static final class ProbeRegistry extends InterceptorRegistry {

        private List<Object> registered() {
            return getInterceptors();
        }
    }
}
