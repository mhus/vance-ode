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
 * The caller sent something this endpoint will not serve.
 *
 * <p>Exists so that a controller can tell its own refusal apart from a failure
 * inside the source it is fronting. Both used to surface as
 * {@link IllegalArgumentException}, and a single handler mapped the lot to 400 —
 * which told a caller "your request was wrong" about a source that had simply
 * fallen over. That matters beyond tidiness: a reader is expected to back off
 * from a 5xx and not from a 400, so a dead source answered as 400 keeps getting
 * asked.
 *
 * <p>Extends {@link IllegalArgumentException} rather than replacing it, so that
 * validation written before this existed keeps its meaning where it is thrown
 * from a record's compact constructor during body binding — that path arrives as
 * a message-conversion failure and is answered as 400 on its own.
 */
public class OdeBadRequestException extends IllegalArgumentException {

    public OdeBadRequestException(String message) {
        super(message);
    }

    public OdeBadRequestException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * The message an error body should carry for {@code e}.
     *
     * <p>Never the literal string {@code "null"}: an exception thrown without a
     * message (a bare {@code NullPointerException}, say) would otherwise produce
     * {@code {"error":"...","message":"null"}}, which is exactly the answer
     * {@link OdeErrorResponse} documents must not happen. The class name is not
     * much, but it names the failure.
     */
    public static String describe(Throwable e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }
}
