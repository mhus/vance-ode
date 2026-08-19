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
 * The answer to a signal: what happened, and optionally a sentence about why.
 *
 * <p>{@code detail} is for the operator reading a log, not for the reader who
 * clicked — Vancetope deliberately reports "reported" and nothing more, since
 * only you know what a report leads to.
 */
public record OdeSignalResponse(OdeSignalOutcome outcome, String detail) {

    public OdeSignalResponse {
        if (outcome == null) {
            outcome = OdeSignalOutcome.REJECTED;
        }
        if (detail == null) {
            detail = "";
        }
    }

    public static OdeSignalResponse of(OdeSignalOutcome outcome) {
        return new OdeSignalResponse(outcome, "");
    }
}
