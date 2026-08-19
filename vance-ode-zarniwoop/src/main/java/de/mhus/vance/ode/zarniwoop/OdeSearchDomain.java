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

/**
 * Subject-area hint used when Vancetope picks between providers for a research
 * plan. Closed and mirrored for the same reason as {@link OdeSearchModality}.
 *
 * <p>Distinct from modality, and the difference is worth keeping straight:
 * modality is <i>what kind of result</i> comes back, domain is <i>what the
 * source is about</i>. A PDF archive of court rulings is
 * {@code modality=PDF, domain=INTERNAL}.
 */
public enum OdeSearchDomain {
    GENERAL,
    NEWS,
    ACADEMIC,
    ENCYCLOPEDIA,
    INTERNAL,
    BOOK,
    CODE
}
