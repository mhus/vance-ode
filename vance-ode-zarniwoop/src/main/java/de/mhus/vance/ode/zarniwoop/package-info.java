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
/**
 * Answers Vancetope research queries out of this application's own index.
 *
 * <p>Implement {@link de.mhus.vance.ode.zarniwoop.SearchSource}, publish it as a
 * bean, and this module exposes the REST contract Zarniwoop speaks. Nothing
 * happens without that bean — a library must not start serving endpoints merely
 * by being on the classpath.
 *
 * <p><b>Searching is not the same act as being read.</b> The sibling module
 * {@code vance-ode-centauri} serves time-ordered streams a reader scrolls; this
 * one answers a question. An application may implement both interfaces, and
 * many will, but neither implies the other: a search index has no chronology to
 * page through, and a feed has no query to answer.
 *
 * <p><b>What the contract deliberately does not carry:</b>
 * <ul>
 *   <li><b>No reader identity.</b> Not a header, not a field. A search query is
 *       not a reading history, and personalised search is a decision with its
 *       own justification — leaving the field out means nobody can quietly
 *       start relying on it.
 *   <li><b>No prompt hint.</b> Zarniwoop can show a provider's text to the
 *       model, but text from a foreign service in a system prompt is a separate
 *       question. Until it is answered, there is no field to fill.
 *   <li><b>No cursor.</b> Search does not paginate here; it has
 *       {@code maxResults}. A caller that wants a continuous stream wants the
 *       feed module.
 * </ul>
 *
 * <p>{@code @NullMarked} package — references are non-null by default unless
 * annotated {@code @Nullable}.
 */
@NullMarked
package de.mhus.vance.ode.zarniwoop;

import org.jspecify.annotations.NullMarked;
