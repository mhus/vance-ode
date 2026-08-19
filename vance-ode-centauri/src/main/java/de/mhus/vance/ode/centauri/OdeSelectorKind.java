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
 * The shape of a selector, so a {@link OdeSelectorMode#FREEFORM} source can
 * say what kind of free text it expects.
 */
public enum OdeSelectorKind {

    /** An entry of this source's own taxonomy. */
    CATEGORY,

    /** A tag, without its leading marker. */
    HASHTAG,

    /** A single author or account. */
    ACCOUNT,

    /** A whole-instance variant, e.g. local versus federated. */
    PUBLIC
}
