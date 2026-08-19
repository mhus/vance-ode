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
package de.mhus.vance.ode.ursa;

import java.util.Map;
import java.util.Optional;
import lombok.Builder;
import lombok.Value;
import org.jspecify.annotations.Nullable;

/**
 * What a fired event returned.
 *
 * <p>Exactly one of {@link #runId} and {@link #output} is meaningful,
 * and which one depends on the event's configuration rather than on the
 * caller: a synchronous script answers with its result, a spawn answers
 * with a handle to poll.
 *
 * <p>The field names are Ode's, not the wire's. The brain still calls
 * these {@code workflowName} and {@code workflowRunId} for historical
 * reasons — they carry recipe and script targets too — and repeating a
 * name that its own specification flags as misleading would be a poor
 * thing for an SDK to do.
 */
@Value
@Builder
public class EventResult {

    /** Event name as fired. */
    String event;

    /**
     * What the event dispatched to: a recipe name, a workflow name, or
     * {@code script:<path>}.
     */
    String target;

    /**
     * Process or workflow run id for a spawn, {@code null} for a
     * synchronous script.
     */
    @Nullable String runId;

    /**
     * The action's result, {@code null} when there is none — a spawn, an
     * {@code async: true} event, or one that withholds its output.
     */
    @Nullable Map<String, Object> output;

    /**
     * The result as text, for the common case.
     *
     * <p>A script returning a scalar arrives under {@code output.value};
     * that is the brain's mapping convention, not ours, and knowing it so
     * the caller does not have to is most of what this method is for.
     * Empty when the event produced no output or produced something that
     * is not a single value.
     */
    public Optional<String> text() {
        if (output == null) {
            return Optional.empty();
        }
        Object value = output.get("value");
        return value instanceof String s ? Optional.of(s) : Optional.empty();
    }

    /** {@code true} when the event ran to completion and produced something. */
    public boolean hasOutput() {
        return output != null && !output.isEmpty();
    }
}
