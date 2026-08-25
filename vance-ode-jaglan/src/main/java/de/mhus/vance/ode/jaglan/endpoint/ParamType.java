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
package de.mhus.vance.ode.jaglan.endpoint;

/**
 * What one parameter accepts.
 *
 * <p>Five types, and the list is short on purpose: everything here has to be
 * expressible in a query string, so this is a vocabulary of scalars and
 * choices rather than a type system.
 *
 * <p><b>The wire names are borrowed, not invented.</b> They are the same words
 * Vancetope's form-field descriptor uses for the same five things, so a
 * declaration served from here can be rendered as a form on the other side
 * without a translation table in between. Nothing enforces the match — this
 * module deliberately shares no classes with the reader — which is exactly why
 * it is written down: a sixth type invented here with a name of its own is the
 * beginning of that table.
 */
public enum ParamType {

    /** Any text. */
    STRING("string"),

    /** A whole number. Refused, not truncated, when it is not one. */
    INTEGER("integer"),

    /** {@code true} or {@code false}, spelled out. */
    BOOLEAN("boolean"),

    /** One of a declared set of values. */
    SELECT("select"),

    /**
     * Several of a declared set of values — {@code tag=a&tag=b}.
     *
     * <p>The one type for which a repeated parameter is an answer rather than a
     * mistake, which is the whole reason
     * {@link de.mhus.vance.ode.jaglan.OdeQuery} keeps a list per name.
     */
    MULTI_SELECT("multi_select");

    private final String wireName;

    ParamType(String wireName) {
        this.wireName = wireName;
    }

    /** The name this type carries in the served declaration. */
    public String wireName() {
        return wireName;
    }

    /** Whether more than one value of this parameter is meaningful. */
    public boolean repeatable() {
        return this == MULTI_SELECT;
    }

    /** Whether this type draws from a declared set of choices. */
    public boolean fromChoices() {
        return this == SELECT || this == MULTI_SELECT;
    }
}
