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

import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * The POST body of a search, before validation.
 *
 * <p>Separate from {@link OdeSearchQuery} on purpose. This one is what arrived
 * over the wire and every field may be missing or wrong; that one is what a
 * source is handed and is guaranteed complete and declared. Deserialising
 * straight into the validated record would force its constructor to accept
 * nulls it exists to rule out, and the type would stop meaning anything.
 *
 * @param maxResults absent or non-positive means „caller has no preference"; the
 *                   controller picks a default and clamps.
 *                   <p><b>Boxed, and it has to be.</b> Jackson 3 fails
 *                   deserialisation when a JSON field for a primitive is
 *                   missing, and that failure happens before any handler runs —
 *                   the caller would get a bodiless 400 with nothing to read,
 *                   for a field the contract calls optional. A primitive cannot
 *                   express absence, so it must not stand where absence is
 *                   allowed. The same reasoning applies to any optional number
 *                   added here later.
 */
public record OdeSearchRequestBody(
        String query,
        @Nullable OdeSearchModality modality,
        @Nullable OdeSearchTier tier,
        @Nullable Integer maxResults,
        @Nullable String locale,
        Map<String, Object> expertParams) {

    public OdeSearchRequestBody {
        expertParams = expertParams == null ? Map.of() : Map.copyOf(expertParams);
    }
}
