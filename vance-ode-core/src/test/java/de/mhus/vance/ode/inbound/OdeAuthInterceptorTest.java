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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * The guard's decisions, which are the ones that open or close the endpoint.
 *
 * <p>Every test here is about a way in that should not exist: a missing header
 * reaching the validator, an exception reading as a pass, a shared secret still
 * working after the application took over the decision.
 */
class OdeAuthInterceptorTest {

    private static final String PATH = "/ode/search";

    private final MockHttpServletResponse response = new MockHttpServletResponse();

    // ── static shared secret ─────────────────────────────────────────

    @Test
    void sharedSecret_correctToken_passes() {
        var interceptor = new OdeAuthInterceptor(endpoint("s3cret"));

        assertThat(interceptor.preHandle(request("s3cret"), response, null)).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void sharedSecret_wrongToken_isRefused() {
        var interceptor = new OdeAuthInterceptor(endpoint("s3cret"));

        assertThat(interceptor.preHandle(request("nope"), response, null)).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("Bearer");
    }

    @Test
    void sharedSecret_noHeader_isRefused() {
        var interceptor = new OdeAuthInterceptor(endpoint("s3cret"));

        assertThat(interceptor.preHandle(request(null), response, null)).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void noSecretAndNoService_passesEverything() {
        var interceptor = new OdeAuthInterceptor(endpoint(""));

        assertThat(interceptor.preHandle(request(null), response, null)).isTrue();
    }

    // ── delegating to an auth service ────────────────────────────────

    @Test
    void authService_allow_publishesTheCaller() {
        var service = new RecordingAuthService(
                OdeAuthDecision.allow(OdeCaller.of("acme", Map.of("plan", "pro"))));
        var interceptor = new OdeAuthInterceptor(endpoint(""), service);
        MockHttpServletRequest request = request("t-1");

        assertThat(interceptor.preHandle(request, response, null)).isTrue();

        OdeCaller caller = (OdeCaller) request.getAttribute(OdeCaller.ATTRIBUTE);
        assertThat(caller).isNotNull();
        assertThat(caller.id()).isEqualTo("acme");
        assertThat(caller.attributes()).containsEntry("plan", "pro");
    }

    @Test
    void authService_seesTheTokenAndTheNormalisedPath() {
        var service = new RecordingAuthService(OdeAuthDecision.allow(OdeCaller.of("acme")));
        var interceptor = new OdeAuthInterceptor(endpoint("", "/ode/search/"), service);

        interceptor.preHandle(request("t-1"), response, null);

        assertThat(service.tokens).containsExactly("t-1");
        assertThat(service.paths).containsExactly("/ode/search");
    }

    @Test
    void authService_unauthenticated_isRefusedWithoutACaller() {
        var service = new RecordingAuthService(OdeAuthDecision.unauthenticated("unknown token"));
        var interceptor = new OdeAuthInterceptor(endpoint(""), service);
        MockHttpServletRequest request = request("t-1");

        assertThat(interceptor.preHandle(request, response, null)).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(request.getAttribute(OdeCaller.ATTRIBUTE)).isNull();
    }

    /** A suspended account is not the same problem as an unknown token. */
    @Test
    void authService_forbidden_isRefusedWith403() {
        var service = new RecordingAuthService(OdeAuthDecision.forbidden("subscription lapsed"));
        var interceptor = new OdeAuthInterceptor(endpoint(""), service);

        assertThat(interceptor.preHandle(request("t-1"), response, null)).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
    }

    /** The reason is for the log; the party being refused learns nothing. */
    @Test
    void refusal_carriesNoBody() throws Exception {
        var service = new RecordingAuthService(OdeAuthDecision.forbidden("subscription lapsed"));
        var interceptor = new OdeAuthInterceptor(endpoint(""), service);

        interceptor.preHandle(request("t-1"), response, null);

        assertThat(response.getContentAsString()).isEmpty();
    }

    @Test
    void authService_throwing_failsClosed() {
        var service = new ThrowingAuthService();
        var interceptor = new OdeAuthInterceptor(endpoint(""), service);

        assertThat(interceptor.preHandle(request("t-1"), response, null)).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void authService_returningNull_failsClosed() {
        var service = new RecordingAuthService(null);
        var interceptor = new OdeAuthInterceptor(endpoint(""), service);

        assertThat(interceptor.preHandle(request("t-1"), response, null)).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
    }

    /**
     * Deciding that no token at all is acceptable is a decision about whether
     * the endpoint is public, and it is made by publishing the bean or not.
     */
    @Test
    void authService_isNotConsultedWithoutABearerHeader() {
        var service = new RecordingAuthService(OdeAuthDecision.allow(OdeCaller.of("acme")));
        var interceptor = new OdeAuthInterceptor(endpoint(""), service);

        assertThat(interceptor.preHandle(request(null), response, null)).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(service.tokens).isEmpty();
    }

    @Test
    void authService_isNotConsultedForAnEmptyBearerHeader() {
        var service = new RecordingAuthService(OdeAuthDecision.allow(OdeCaller.of("acme")));
        var interceptor = new OdeAuthInterceptor(endpoint(""), service);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", PATH);
        request.addHeader("Authorization", "Bearer   ");

        assertThat(interceptor.preHandle(request, response, null)).isFalse();
        assertThat(service.tokens).isEmpty();
    }

    /** Two definitions of "valid" is the one thing this must never become. */
    @Test
    void authService_present_makesTheConfiguredSecretIrrelevant() {
        var service = new RecordingAuthService(OdeAuthDecision.unauthenticated());
        var interceptor = new OdeAuthInterceptor(endpoint("s3cret"), service);

        assertThat(interceptor.preHandle(request("s3cret"), response, null)).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(service.tokens).containsExactly("s3cret");
    }

    // ── helpers ──────────────────────────────────────────────────────

    private static MockHttpServletRequest request(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", PATH);
        if (token != null) {
            request.addHeader("Authorization", "Bearer " + token);
        }
        return request;
    }

    private static OdeInboundEndpoint endpoint(String apiKey) {
        return endpoint(apiKey, PATH);
    }

    private static OdeInboundEndpoint endpoint(String apiKey, String path) {
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

    private static final class RecordingAuthService implements OdeAuthService {

        private final OdeAuthDecision decision;
        private final List<String> tokens = new ArrayList<>();
        private final List<String> paths = new ArrayList<>();

        private RecordingAuthService(OdeAuthDecision decision) {
            this.decision = decision;
        }

        @Override
        public OdeAuthDecision authenticate(String presentedToken, String endpointPath) {
            tokens.add(presentedToken);
            paths.add(endpointPath);
            return decision;
        }
    }

    private static final class ThrowingAuthService implements OdeAuthService {

        @Override
        public OdeAuthDecision authenticate(String presentedToken, String endpointPath) {
            throw new IllegalStateException("token store unreachable");
        }
    }
}
