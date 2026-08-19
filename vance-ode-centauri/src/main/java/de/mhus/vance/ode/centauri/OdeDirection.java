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
 * Which way a page runs relative to the cursor.
 *
 * <p>A source that cannot serve {@link #NEWER} says so in
 * {@link OdeCapabilities#supportsNewerDirection()} and never sees the value:
 * the controller rejects the request before it reaches
 * {@link FeedSource#items}.
 */
public enum OdeDirection {

    /** Backwards in time — the endless scroll. Cursor is an upper bound. */
    OLDER,

    /** Forwards in time — pull-to-refresh. Cursor is a lower bound. */
    NEWER
}
