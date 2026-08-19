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

/**
 * Body of a refused request: a short machine-readable code and a sentence for
 * whoever is reading the log.
 *
 * <p>Both fields matter. Without the code the caller cannot distinguish a
 * malformed request from an unsupported one; without the sentence the person
 * debugging a new source has nothing to go on.
 */
public record OdeErrorResponse(String error, String message) {

    public OdeErrorResponse {
        if (error == null || error.isBlank()) {
            error = "bad_request";
        }
        if (message == null) {
            message = "";
        }
    }
}
