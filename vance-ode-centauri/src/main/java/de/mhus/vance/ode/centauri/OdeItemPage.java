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

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * One page of entries.
 *
 * <p><b>Order it chronologically</b> — descending for
 * {@link OdeDirection#OLDER}, ascending for {@link OdeDirection#NEWER}.
 * Personalising <i>which</i> entries appear is fine; personalising their order
 * is not, because the reader merges your page with other sources' pages on the
 * timestamp. A per-reader ranking would not look broken, it would quietly
 * produce a wrong sequence. This module logs a warning when a page arrives out
 * of order, so the mistake surfaces where it can be fixed.
 *
 * <p>{@code nextCursor} is opaque to Vancetope and entirely yours. Two things
 * are worth knowing about how it is used: the reader may cut a page in the
 * middle and resume from a single entry's id instead, and it will keep asking
 * as long as {@code hasMore} is true — so an empty page with {@code hasMore}
 * needs a {@code nextCursor} that actually moves, or the reader loops.
 */
public record OdeItemPage(
        List<OdeItem> items,
        @Nullable String nextCursor,
        boolean hasMore) {

    public OdeItemPage {
        items = items == null ? List.of() : List.copyOf(items);
    }

    /** No entries, nothing to come. */
    public static OdeItemPage empty() {
        return new OdeItemPage(List.of(), null, false);
    }

    /** The last page of a stream. */
    public static OdeItemPage last(List<OdeItem> items) {
        return new OdeItemPage(items, null, false);
    }
}
