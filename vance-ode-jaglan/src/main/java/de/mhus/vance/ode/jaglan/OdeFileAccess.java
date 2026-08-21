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
package de.mhus.vance.ode.jaglan;

/**
 * What you allow on your files. Declared, so the reader renders a read-only
 * editor instead of offering a save it is going to be refused.
 *
 * <p>There is deliberately no {@code UNKNOWN} here even though the reader has
 * one: unknown is what the <em>reader</em> concludes when it cannot reach you.
 * You always know your own answer.
 */
public enum OdeFileAccess {

    /** Readable only. Writes and deletes are refused. */
    READ_ONLY,

    /** Readable and writable. */
    READ_WRITE
}
