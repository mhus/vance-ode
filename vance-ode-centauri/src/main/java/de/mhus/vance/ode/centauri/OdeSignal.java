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
 * The back channel's closed vocabulary.
 *
 * <p>The admission rule on Vancetope's side is that <b>a signal describes the
 * item, not the reader</b>: "this entry is misfiled" is a checkable statement
 * any aggregator can act on, while "I like this" or "hide this" describe the
 * person. That is why there is no LIKE here, and why a source is never asked
 * to store reader preferences.
 *
 * <p>Anything this set does not cover reaches you as a deep link into your own
 * UI instead — see {@link OdeItem#controlUrl()}. That is deliberate: it keeps
 * this vocabulary small while leaving you free to offer whatever your own
 * interface can express.
 */
public enum OdeSignal {

    /** This entry is wrong. Carries an {@link OdeReportReason}. */
    REPORT,

    /**
     * Produce something for this entry and keep it. Carries an
     * {@link OdeRequestKind}.
     *
     * <p>Fire-and-forget: the result is not expected in the response but on
     * the next read of the item.
     */
    REQUEST
}
