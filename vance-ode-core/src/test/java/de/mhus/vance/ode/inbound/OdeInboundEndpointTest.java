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

import org.junit.jupiter.api.Test;

/**
 * The path normalisation the guard depends on.
 *
 * <p>Worth its own test because the failure mode is silent and it opens rather
 * than closes: a configured trailing slash used to build the pattern
 * {@code "/ode/feed/" + "/**"}, which matches nothing, while
 * {@code @RequestMapping} kept serving the endpoints unguarded.
 */
class OdeInboundEndpointTest {

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

    @Test
    void normalisedPath_alreadyCanonical_isUnchanged() {
        assertThat(endpoint("/ode/feed", "k").normalisedPath()).isEqualTo("/ode/feed");
    }

    @Test
    void normalisedPath_trailingSlash_isDropped() {
        assertThat(endpoint("/ode/feed/", "k").normalisedPath()).isEqualTo("/ode/feed");
    }

    @Test
    void normalisedPath_severalTrailingSlashes_areDropped() {
        assertThat(endpoint("/ode/feed///", "k").normalisedPath()).isEqualTo("/ode/feed");
    }

    @Test
    void normalisedPath_missingLeadingSlash_isAdded() {
        assertThat(endpoint("ode/feed", "k").normalisedPath()).isEqualTo("/ode/feed");
    }

    @Test
    void normalisedPath_duplicatedLeadingSlash_isCollapsed() {
        assertThat(endpoint("//ode/feed", "k").normalisedPath()).isEqualTo("/ode/feed");
    }

    @Test
    void normalisedPath_surroundingWhitespace_isTrimmed() {
        assertThat(endpoint("  /ode/search  ", "k").normalisedPath()).isEqualTo("/ode/search");
    }

    @Test
    void isSecured_blankKey_meansNoCheck() {
        assertThat(endpoint("/ode/feed", "  ").isSecured()).isFalse();
        assertThat(endpoint("/ode/feed", "k").isSecured()).isTrue();
    }
}
