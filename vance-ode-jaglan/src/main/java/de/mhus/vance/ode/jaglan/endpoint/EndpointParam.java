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

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

/**
 * One declared parameter of an endpoint.
 *
 * <p>Everything here is checked when the declaration is built, not when a
 * request arrives: a malformed declaration is a developer's mistake and belongs
 * refused once, at the place a person can fix it, rather than turning into a
 * refused read for somebody else later.
 *
 * <p><b>Six names are refused outright</b>, and this is the least obvious thing
 * in the class. {@code path} addresses the file on the wire endpoint;
 * {@code kind}, {@code entry}, {@code mode} and {@code caption} are the
 * reader's reference grammar — how to interpret a document, where inside an
 * application to land, how an embed is drawn — and {@code download} is its
 * content endpoint's disposition switch. The reader strips all five before
 * forwarding a query. A parameter with one of those names would therefore never
 * arrive, and a required one would make every read fail for a reason nothing in
 * this process can see.
 *
 * @param name         the query-string name
 * @param type         what it accepts
 * @param required     whether a read without it is refused. A required
 *                     parameter with a default is a contradiction and is
 *                     refused: the default would mean it is never missing
 * @param defaultValue what the endpoint reads when it is absent, {@code null}
 *                     for no default. As a string, because that is how it
 *                     arrives — and it is validated against {@link #type} here,
 *                     so a default that could never be sent cannot be declared
 * @param description  one line, for the served declaration. Not decoration:
 *                     it is what an agent reads to decide what to send
 * @param choices      the allowed values for {@code select} / {@code
 *                     multi_select}, empty for the other types
 */
public record EndpointParam(
        String name,
        ParamType type,
        boolean required,
        @Nullable String defaultValue,
        String description,
        List<String> choices) {

    /**
     * Query-string names this module refuses to declare.
     *
     * <p>Kept as a literal rather than shared with the reader: the two ends
     * implement the same contract separately, which is what makes it one. The
     * cost is that a name added to the reader's list has to be added here too,
     * and it is a real cost rather than a theoretical one — the list grew from
     * three to six within a day of being written, when the reader's reference
     * grammar turned out to own {@code entry}, {@code mode} and {@code caption}
     * as well. Being long is the safe direction: a name refused here that the
     * reader would have forwarded costs one rename at declaration time, and a
     * name missing here is a parameter that vanishes in flight.
     *
     * <p>{@code token} is the one entry that is not grammar but credentials:
     * the reader's content route authenticates a browser with
     * {@code ?token=<jwt>}, and a source that declared a {@code token}
     * parameter would be asking to be handed the caller's session.
     */
    public static final List<String> RESERVED_NAMES =
            List.of("path", "kind", "entry", "mode", "caption", "download", "token");

    private static final Pattern NAME = Pattern.compile("[a-zA-Z][a-zA-Z0-9_-]*");

    public EndpointParam {
        if (name == null || !NAME.matcher(name).matches()) {
            throw new IllegalArgumentException(
                    "parameter name must match " + NAME.pattern() + ", got '" + name + "'");
        }
        if (RESERVED_NAMES.contains(name.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("parameter name '" + name
                    + "' is reserved and would be stripped before it reached this source; "
                    + "reserved: " + RESERVED_NAMES);
        }
        if (type == null) {
            throw new IllegalArgumentException("parameter '" + name + "' has no type");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("parameter '" + name + "' has no description, "
                    + "and the description is what a caller reads to know what to send");
        }
        choices = choices == null ? List.of() : List.copyOf(choices);
        if (type.fromChoices() == choices.isEmpty()) {
            throw new IllegalArgumentException("parameter '" + name + "' of type "
                    + type.wireName() + (choices.isEmpty()
                            ? " needs choices" : " must not declare choices"));
        }
        defaultValue = defaultValue == null || defaultValue.isBlank() ? null : defaultValue.strip();
        if (required && defaultValue != null) {
            throw new IllegalArgumentException("parameter '" + name
                    + "' is required and has a default, which cannot both be true");
        }
        if (defaultValue != null) {
            // The default travels the same road as a sent value, so it has to
            // survive the same check. A default outside its own choices is the
            // kind of thing that works until the day somebody omits the
            // parameter.
            String rejected = reasonToRefuse(defaultValue, type, choices);
            if (rejected != null) {
                throw new IllegalArgumentException(
                        "the default of parameter '" + name + "' " + rejected);
            }
        }
    }

    /** An optional parameter of a scalar type. */
    public static EndpointParam optional(
            String name, ParamType type, @Nullable String defaultValue, String description) {
        return new EndpointParam(name, type, false, defaultValue, description, List.of());
    }

    /** A parameter a read cannot omit. */
    public static EndpointParam required(String name, ParamType type, String description) {
        return new EndpointParam(name, type, true, null, description, List.of());
    }

    /** One of {@code choices}, with a default. */
    public static EndpointParam select(
            String name, List<String> choices, @Nullable String defaultValue,
            String description) {
        return new EndpointParam(
                name, ParamType.SELECT, false, defaultValue, description, choices);
    }

    /** Any number of {@code choices}. */
    public static EndpointParam multiSelect(
            String name, List<String> choices, String description) {
        return new EndpointParam(
                name, ParamType.MULTI_SELECT, false, null, description, choices);
    }

    /**
     * Why {@code value} is not acceptable for this parameter, or {@code null}
     * when it is.
     *
     * <p>A message rather than a boolean, because every caller of this turns it
     * into a refusal the other side has to be able to act on — and "the value
     * is wrong" is not something anybody can act on.
     */
    @Nullable String reasonToRefuse(String value) {
        return reasonToRefuse(value, type, choices);
    }

    private static @Nullable String reasonToRefuse(
            String value, ParamType type, List<String> choices) {
        return switch (type) {
            case STRING -> null;
            case INTEGER -> isInteger(value) ? null : "is not a whole number: '" + value + "'";
            case BOOLEAN -> "true".equals(value) || "false".equals(value)
                    ? null : "is not 'true' or 'false': '" + value + "'";
            case SELECT, MULTI_SELECT -> choices.contains(value)
                    ? null : "is not one of " + choices + ": '" + value + "'";
        };
    }

    private static boolean isInteger(String value) {
        try {
            Integer.parseInt(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
