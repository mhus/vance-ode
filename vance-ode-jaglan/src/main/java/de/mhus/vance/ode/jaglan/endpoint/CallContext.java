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

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.mhus.vance.ode.inbound.OdeBadRequestException;
import de.mhus.vance.ode.jaglan.OdeQuery;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.dataformat.yaml.YAMLMapper;
import tools.jackson.dataformat.yaml.YAMLWriteFeature;

/**
 * One call: the parameters that arrived, and the answer that goes back.
 *
 * <h2>The request side refuses before the endpoint runs</h2>
 *
 * <p>Constructing this validates the whole query against the declaration —
 * every value against its type, every choice against its set, every required
 * parameter present — and an undeclared name is refused outright rather than
 * ignored. So by the time {@link MountEndpoint#handle} is called there is
 * nothing left to check, and more to the point: there is no way for the endpoint
 * to read a parameter it did not declare, and no way for a caller to send one
 * that quietly does nothing.
 *
 * <p>Refusals are {@link OdeBadRequestException}, which the wire layer turns
 * into a 400 carrying the reason. That is the load-bearing part — a refusal the
 * reader can report and a person can act on. The alternative, an exception the
 * boundary does not recognise, is a 500, and a 500 tells the reader to back off
 * and try again, which does not fix a wrong parameter.
 *
 * <h2>The answer is held, not streamed</h2>
 *
 * <p>{@link #reply} takes bytes and keeps them. Computed content has no length
 * until it exists, so there is nothing to stream from — and the size at which
 * holding an answer in memory becomes a problem is the size at which a computed
 * view was the wrong shape to begin with: the whole design rests on answering
 * inside a single read, with no job queue behind it. Something that large
 * belongs materialised under an ordinary path.
 *
 * <p>An endpoint that returns without replying is a bug in the endpoint, not a
 * refusal, and is reported as one.
 */
public final class CallContext {

    /** The Maven coordinates of the writer {@link #replyYaml} needs. */
    private static final String YAML_ARTIFACT =
            "tools.jackson.dataformat:jackson-dataformat-yaml";

    /**
     * Whether the YAML writer can be loaded at all.
     *
     * <p>Checked by name rather than by referring to the type, and that is the
     * whole point: this class must remain usable when the writer is absent.
     * It was not, once — the mapper was a static field of this class, so a
     * classpath without the writer failed in the static initialiser and took
     * <em>everything</em> with it: the parameter validation, the byte replies,
     * the plain-text replies, none of which have anything to do with YAML. The
     * error a caller saw named a Jackson class and a line number in here, and
     * every read after the first said only "could not initialise
     * CallContext".
     *
     * <p>A missing library is now one refusal, from the one method that needs
     * it, naming the artifact to add.
     */
    private static final boolean YAML_AVAILABLE =
            canLoad("tools.jackson.dataformat.yaml.YAMLMapper");

    private static boolean canLoad(String className) {
        try {
            Class.forName(className, false, CallContext.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    private final EndpointSpec spec;
    private final Map<String, List<String>> values;
    private @Nullable byte[] answer;

    private CallContext(EndpointSpec spec, Map<String, List<String>> values) {
        this.spec = spec;
        this.values = values;
    }

    /**
     * Validate {@code query} against {@code spec} and build the context.
     *
     * @throws OdeBadRequestException when the query does not fit the declaration
     */
    public static CallContext of(EndpointSpec spec, OdeQuery query) {
        Map<String, List<String>> values = new LinkedHashMap<>();
        for (String name : query.names()) {
            EndpointParam param = spec.param(name).orElseThrow(() ->
                    new OdeBadRequestException("'" + spec.path() + "' has no parameter '"
                            + name + "'; it accepts " + declaredNames(spec)));
            List<String> given = query.all(name);
            if (given.size() > 1 && !param.type().repeatable()) {
                // Keeping the first would be the quiet version of this, and the
                // caller would get an answer about one of two things they asked
                // about with nothing to indicate which.
                throw new OdeBadRequestException("parameter '" + name + "' of '" + spec.path()
                        + "' takes one value but was given " + given.size());
            }
            for (String value : given) {
                String rejected = param.reasonToRefuse(value);
                if (rejected != null) {
                    throw new OdeBadRequestException(
                            "parameter '" + name + "' of '" + spec.path() + "' " + rejected);
                }
            }
            values.put(name, List.copyOf(given));
        }
        for (EndpointParam param : spec.params()) {
            if (param.required() && !values.containsKey(param.name())) {
                throw new OdeBadRequestException("'" + spec.path() + "' requires the parameter '"
                        + param.name() + "': " + param.description());
            }
        }
        return new CallContext(spec, values);
    }

    /** A call with no parameters at all — a plain read of the endpoint's path. */
    public static CallContext of(EndpointSpec spec) {
        return of(spec, OdeQuery.EMPTY);
    }

    /** What was asked for. */
    public EndpointSpec spec() {
        return spec;
    }

    /** The path this endpoint answers at. */
    public String path() {
        return spec.path();
    }

    /** Whether the caller sent any parameter — a plain read sends none. */
    public boolean parameterised() {
        return !values.isEmpty();
    }

    // ── the request side ─────────────────────────────────────────────

    /**
     * The value of {@code name}: what was sent, else its declared default, else
     * {@code null}.
     *
     * @throws IllegalStateException if {@code name} was never declared — an
     *         endpoint reading a parameter it did not declare is a bug in the
     *         endpoint, and no request can produce this
     */
    public @Nullable String text(String name) {
        EndpointParam param = declared(name);
        List<String> given = values.get(name);
        if (given != null && !given.isEmpty()) {
            return given.get(0);
        }
        return param.defaultValue();
    }

    /** The value of {@code name}, or {@code fallback} when neither sent nor defaulted. */
    public String text(String name, String fallback) {
        String value = text(name);
        return value == null ? fallback : value;
    }

    /**
     * The value of {@code name} as a number, or {@code null} when absent.
     *
     * <p>Never throws for a malformed number: an {@code INTEGER} parameter that
     * is not one was refused at construction.
     */
    public @Nullable Integer integer(String name) {
        requireType(name, ParamType.INTEGER);
        String value = text(name);
        return value == null ? null : Integer.valueOf(value);
    }

    /** The value of {@code name} as a number, or {@code fallback} when absent. */
    public int integer(String name, int fallback) {
        Integer value = integer(name);
        return value == null ? fallback : value;
    }

    /** The value of {@code name} as a flag; {@code false} when absent and undefaulted. */
    public boolean flag(String name) {
        requireType(name, ParamType.BOOLEAN);
        return "true".equals(text(name));
    }

    /**
     * Every value of {@code name} — the point of a {@code multi_select}.
     *
     * <p>Empty when nothing was sent, which for a set of choices is the honest
     * answer: no selection, not the whole set. An endpoint that means "all" when
     * nothing was chosen has to say so itself.
     */
    public List<String> values(String name) {
        declared(name);
        return values.getOrDefault(name, List.of());
    }

    // ── the response side ────────────────────────────────────────────

    /** Answer with these bytes. The content type is the one the spec declares. */
    public void reply(byte[] content) {
        if (answer != null) {
            throw new IllegalStateException("'" + spec.path() + "' answered twice");
        }
        if (content == null) {
            throw new IllegalStateException("'" + spec.path() + "' replied with null");
        }
        answer = content.clone();
    }

    /** Answer with text, UTF-8. */
    public void reply(String content) {
        reply(content.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Answer with {@code content} serialised as YAML.
     *
     * <p>Here because otherwise every source writes its own serialiser for the
     * same job, and hand-built YAML is the one output format where a value
     * nobody quoted turns into a different document rather than an error.
     *
     * <p><b>The only method in this package that needs a YAML writer.</b> An
     * endpoint answering with {@link #reply(byte[])} or {@link #reply(String)}
     * works on a classpath without one; this one refuses, and says which
     * artifact is missing.
     *
     * @throws IllegalStateException when the writer is not on the classpath
     */
    public void replyYaml(Object content) {
        if (!YAML_AVAILABLE) {
            throw new IllegalStateException(missingWriter());
        }
        String yaml;
        try {
            yaml = Yaml.write(content);
        } catch (LinkageError e) {
            // The writer is there and something under it is not — Jackson's YAML
            // backend needs snakeyaml-engine, and a classpath assembled by hand
            // can have the one without the other. Caught rather than left to
            // escape, because a bare NoClassDefFoundError names a class nobody
            // in this codebase has heard of. The cause is kept.
            throw new IllegalStateException(missingWriter(), e);
        }
        reply(yaml);
    }

    private String missingWriter() {
        return "'" + spec.path() + "' answers with YAML, but the writer is not usable on "
                + "this classpath: it needs " + YAML_ARTIFACT + " and the snakeyaml-engine "
                + "it depends on. Both are dependencies of this module, so a build that "
                + "resolves normally has them — a runtime without them is usually a "
                + "hand-assembled or stale classpath. Add them, or answer with "
                + "reply(byte[]) instead.";
    }

    /**
     * The bytes the endpoint produced.
     *
     * @throws IllegalStateException when it produced none
     */
    public byte[] answer() {
        if (answer == null) {
            throw new IllegalStateException(
                    "'" + spec.path() + "' returned without answering");
        }
        return answer.clone();
    }

    /** Whether the endpoint has answered yet. */
    public boolean answered() {
        return answer != null;
    }

    // ── helpers ──────────────────────────────────────────────────────

    private EndpointParam declared(String name) {
        return spec.param(name).orElseThrow(() -> new IllegalStateException(
                "'" + spec.path() + "' read the parameter '" + name
                        + "', which it does not declare; it declares " + declaredNames(spec)));
    }

    private void requireType(String name, ParamType expected) {
        EndpointParam param = declared(name);
        if (param.type() != expected) {
            throw new IllegalStateException("'" + spec.path() + "' declares '" + name + "' as "
                    + param.type().wireName() + " and read it as " + expected.wireName());
        }
    }

    private static List<String> declaredNames(EndpointSpec spec) {
        return spec.params().stream().map(EndpointParam::name).toList();
    }

    /**
     * The YAML writer, in a holder so that loading it is deferred to the first
     * document that is actually written as YAML.
     *
     * <p>A nested class is initialised on first use of one of its members, and
     * nothing outside {@link #replyYaml} touches this one. That is what keeps
     * the rest of the class — the validation, the byte replies — independent of
     * whether a YAML library is present at all.
     *
     * <p>The mapper is shared: expensive to build, immutable once built, and
     * safe to use from several threads.
     */
    private static final class Yaml {

        /**
         * Configured for a reader rather than for a parser: no {@code ---} at
         * the top of a file that will be shown in a document viewer, quotes
         * only where they change the meaning, and list indicators indented
         * under their key. What this writes is read by people as often as by
         * machines.
         *
         * <p><b>Empty values are left out entirely</b>, which is a decision
         * about somebody else's document and so worth stating: {@code null} and
         * empty collections carry nothing, and a key present with no value
         * invites the reading "this exists and is broken". A field that has to
         * appear even when unset should be given a value that says so.
         *
         * <p>Timestamps go out as ISO-8601 rather than as epoch decimals, for
         * the same audience: {@code 2026-08-25T08:45:00Z} is a date somebody
         * can check against the question they asked, and {@code 1.787654E9} is
         * not.
         */
        private static final YAMLMapper MAPPER = YAMLMapper.builder()
                .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(YAMLWriteFeature.WRITE_DOC_START_MARKER)
                .enable(YAMLWriteFeature.MINIMIZE_QUOTES)
                // With MINIMIZE_QUOTES on, this is what keeps "0031" and "20"
                // from being written bare and read back as numbers. Not
                // cosmetic: codes with leading zeroes are ordinary values in a
                // news archive, and a silently retyped one is wrong in a way
                // nothing reports.
                .enable(YAMLWriteFeature.ALWAYS_QUOTE_NUMBERS_AS_STRINGS)
                // One line per value. The alternative wraps long text with
                // trailing backslashes, which is valid YAML and unreadable —
                // and these documents are read by people and by agents, not
                // only parsed.
                .disable(YAMLWriteFeature.SPLIT_LINES)
                .enable(YAMLWriteFeature.INDENT_ARRAYS_WITH_INDICATOR)
                .changeDefaultPropertyInclusion(
                        value -> value.withValueInclusion(JsonInclude.Include.NON_EMPTY))
                .build();

        private Yaml() {
        }

        static String write(Object content) {
            return MAPPER.writeValueAsString(content);
        }
    }
}
