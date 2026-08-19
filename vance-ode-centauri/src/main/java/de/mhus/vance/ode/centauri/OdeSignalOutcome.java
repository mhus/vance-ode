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
 * What became of a signal — as much as can honestly be said.
 *
 * <p>There is no value for "acted upon". How you weigh or de-duplicate
 * reports is your business, and Vancetope's UI accordingly says "reported"
 * rather than "category changed". Do not feel obliged to promise more than
 * receipt.
 */
public enum OdeSignalOutcome {

    /** Taken. Nothing further is promised. */
    ACCEPTED,

    /** This source does not accept this signal. Maps to HTTP 501. */
    UNSUPPORTED,

    /** Refused — unknown entry, implausible reason, too many of them. */
    REJECTED
}
