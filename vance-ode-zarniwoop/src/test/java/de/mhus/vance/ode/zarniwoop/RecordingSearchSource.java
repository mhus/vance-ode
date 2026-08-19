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
package de.mhus.vance.ode.zarniwoop;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * A source that records what it was handed. What reaches the source is half the
 * contract — the other half is what never should.
 */
final class RecordingSearchSource implements SearchSource {

    private OdeSearchCapabilities capabilities = new OdeSearchCapabilities(
            Set.of(OdeSearchModality.NEWS, OdeSearchModality.WEB),
            Set.of(OdeSearchDomain.NEWS),
            Set.of(OdeSearchTier.NORMAL),
            25,
            Set.of("desk", "before"),
            false,
            java.time.Duration.ofMinutes(30));

    private final List<OdeSearchQuery> received = new ArrayList<>();
    private OdeSearchResponse response = OdeSearchResponse.of(List.of());
    private @Nullable OdeContentBody body;
    private @Nullable RuntimeException failure;

    RecordingSearchSource withCapabilities(OdeSearchCapabilities caps) {
        this.capabilities = caps;
        return this;
    }

    RecordingSearchSource answering(OdeSearchResponse response) {
        this.response = response;
        return this;
    }

    RecordingSearchSource serving(OdeContentBody body) {
        this.body = body;
        return this;
    }

    RecordingSearchSource failingWith(RuntimeException e) {
        this.failure = e;
        return this;
    }

    List<OdeSearchQuery> received() {
        return List.copyOf(received);
    }

    @Override
    public OdeSearchCapabilities capabilities() {
        return capabilities;
    }

    @Override
    public OdeSearchResponse search(OdeSearchQuery query) {
        received.add(query);
        if (failure != null) {
            throw failure;
        }
        return response;
    }

    @Override
    public Optional<OdeContentBody> content(String contentId) {
        return Optional.ofNullable(body);
    }
}
