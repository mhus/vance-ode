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

/**
 * What an {@link OdeAuthService} decided about one presented token.
 *
 * <p><b>Three outcomes rather than a boolean</b>, because two of the three
 * refusals mean genuinely different things to the caller: a token nobody issued
 * is a configuration mistake somebody has to fix, while a token that was issued
 * and then suspended is an account matter — and only the source can tell them
 * apart. A boolean would have collapsed both into "no" and would have had to be
 * widened later anyway.
 *
 * <p>The vocabulary is deliberately not HTTP: an SPI that speaks in status codes
 * makes every implementation learn the transport. The mapping to 401/403 happens
 * at the boundary, once.
 *
 * @param outcome what to do with the request.
 * @param caller  who is calling. Required for {@link Outcome#ALLOW} and
 *                meaningless otherwise.
 * @param message a sentence for the log, not for the caller — a refusal is
 *                answered with a status and nothing else, because an
 *                unauthenticated party is the last one who should be told why
 *                its token was rejected.
 */
public record OdeAuthDecision(
        Outcome outcome, @Nullable OdeCaller caller, @Nullable String message) {

    /** The three answers to "may this token in?". */
    public enum Outcome {

        /** Token recognised; {@link OdeAuthDecision#caller()} says whose it is. */
        ALLOW,

        /** No such token — unknown, expired, malformed. Answered with 401. */
        UNAUTHENTICATED,

        /**
         * Token recognised but not permitted here — suspended account, an
         * endpoint outside the contract. Answered with 403.
         */
        FORBIDDEN
    }

    public OdeAuthDecision {
        if (outcome == null) {
            throw new IllegalArgumentException("outcome is required");
        }
        if (outcome == Outcome.ALLOW && caller == null) {
            throw new IllegalArgumentException("ALLOW needs a caller");
        }
    }

    public static OdeAuthDecision allow(OdeCaller caller) {
        return new OdeAuthDecision(Outcome.ALLOW, caller, null);
    }

    /** The default refusal: this token buys nothing here. */
    public static OdeAuthDecision unauthenticated() {
        return new OdeAuthDecision(Outcome.UNAUTHENTICATED, null, null);
    }

    public static OdeAuthDecision unauthenticated(String message) {
        return new OdeAuthDecision(Outcome.UNAUTHENTICATED, null, message);
    }

    public static OdeAuthDecision forbidden(String message) {
        return new OdeAuthDecision(Outcome.FORBIDDEN, null, message);
    }
}
