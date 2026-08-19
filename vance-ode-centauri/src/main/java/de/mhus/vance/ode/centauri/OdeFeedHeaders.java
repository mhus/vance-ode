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
 * Header names of the feed contract.
 */
public final class OdeFeedHeaders {

    /**
     * The reader pseudonym, salted per source by the caller.
     *
     * <p>A header rather than a query parameter on purpose: a pseudonym in the
     * URL ends up in every access log and in the cache key of anything in
     * between, which is precisely where a value like this should not be.
     *
     * <p>Sent only on the entry-facing calls — items, body, signal. It never
     * accompanies capabilities or selectors, because those describe the source
     * and not the person asking.
     */
    public static final String READER = "X-Vance-Reader";

    private OdeFeedHeaders() {
        /* constants only */
    }
}
