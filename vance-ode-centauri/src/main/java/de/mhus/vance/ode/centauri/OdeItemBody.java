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

/**
 * The full text of one entry, for sources whose list representation is a
 * teaser.
 *
 * <p>A record rather than a bare string so the response stays a JSON object:
 * a future addition (a content type, a fetch timestamp) then does not change
 * the shape of what is already deployed.
 */
public record OdeItemBody(String body) {

    public OdeItemBody {
        if (body == null) {
            body = "";
        }
    }
}
