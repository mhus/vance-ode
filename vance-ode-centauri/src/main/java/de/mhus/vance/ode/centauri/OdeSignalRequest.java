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

import org.jspecify.annotations.Nullable;

/**
 * One back-channel message about one entry.
 *
 * <p>The argument belonging to the signal is mandatory and checked before you
 * see it: a {@link OdeSignal#REPORT} without a reason and a
 * {@link OdeSignal#REQUEST} without a kind are not messages, and rejecting
 * them at the boundary spares every implementation the same three lines.
 *
 * <p>{@code note} is free text a person typed, capped at
 * {@link #MAX_NOTE_LENGTH}. Treat it as untrusted input from outside your
 * system — it is, and the reader's UI tells them it travels to you.
 *
 * <p>{@code reader} is the same opaque, per-source pseudonym as in
 * {@link OdeItemQuery#reader()} and may be absent. It lets you de-duplicate
 * per reader if you wish; nothing obliges you to.
 */
public record OdeSignalRequest(
        String itemId,
        OdeSignal signal,
        @Nullable OdeReportReason reason,
        @Nullable OdeRequestKind requestKind,
        @Nullable String note,
        @Nullable String reader) {

    public static final int MAX_NOTE_LENGTH = 2000;

    public OdeSignalRequest {
        if (itemId == null || itemId.isBlank()) {
            throw new IllegalArgumentException("itemId is required");
        }
        if (signal == null) {
            throw new IllegalArgumentException("signal is required");
        }
        if (signal == OdeSignal.REPORT && reason == null) {
            throw new IllegalArgumentException("REPORT requires a reason");
        }
        if (signal == OdeSignal.REQUEST && requestKind == null) {
            throw new IllegalArgumentException("REQUEST requires a kind");
        }
        if (note != null) {
            note = note.isBlank() ? null : note.trim();
            if (note != null && note.length() > MAX_NOTE_LENGTH) {
                throw new IllegalArgumentException(
                        "note exceeds " + MAX_NOTE_LENGTH + " characters");
            }
        }
        if (reader != null && reader.isBlank()) {
            reader = null;
        }
    }

    /** Copy carrying the pseudonym the controller read from the header. */
    OdeSignalRequest withReader(@Nullable String value) {
        return new OdeSignalRequest(itemId, signal, reason, requestKind, note, value);
    }
}
