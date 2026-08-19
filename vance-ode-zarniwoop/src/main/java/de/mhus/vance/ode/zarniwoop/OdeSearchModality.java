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
 * What kind of thing is being searched for.
 *
 * <p><b>Closed on purpose, and mirrored on purpose.</b> Vancetope's own
 * {@code SearchModality} is a fixed enum because the LLM tool schemas enumerate
 * its values — a free-text field here would break that guarantee at the far
 * end of the wire. Mirroring it as an enum rather than accepting a string means
 * a source implementer finds out at compile time that the set is closed,
 * instead of at runtime from a deserialisation error.
 *
 * <p>The price is that this list has to be kept in step when Vancetope adds a
 * value. That is the intended trade: a genuinely new modality ({@code LEGAL},
 * {@code PATENT}) is a change to the contract on both sides, not something a
 * single source gets to invent. Map onto the nearest existing value — a news
 * index is {@link #NEWS}, a document archive is {@link #INTERNAL_DOC}.
 */
public enum OdeSearchModality {
    WEB,
    IMAGE,
    VIDEO,
    PDF,
    NEWS,
    ACADEMIC,
    ENCYCLOPEDIA,
    BOOK,
    MAP,
    CODE,
    INTERNAL_DOC,
    RAG
}
