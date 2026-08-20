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
 * Facets — the dimensions your source can be filtered by.
 *
 * <p>In {@code core} rather than in one of the two contract modules because
 * both of them offer the same filter, and a source that serves feeds
 * <em>and</em> search declares it once. That case is not hypothetical: it is
 * the shape of a news archive.
 *
 * <p>A facet is exactly two things — a declaration in your capabilities, and
 * a field in the query you are handed. It never appears on an item or a hit:
 * Vancetope does not filter your entries locally, so there is nothing for it
 * to check them against. What that buys you is stated in
 * {@link de.mhus.vance.ode.facet.OdeFacet}.
 */
@NullMarked
package de.mhus.vance.ode.facet;

import org.jspecify.annotations.NullMarked;
