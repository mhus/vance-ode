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
package de.mhus.vance.ode.kit;

import org.jspecify.annotations.Nullable;

/**
 * What this application says about one kit it serves, without building it.
 *
 * <p>The reason this exists separately from {@link OdeKitBundle}: a reader
 * checks periodically whether anything changed, and assembling a kit to answer
 * „no" would make that check cost what an install costs.
 *
 * @param id how the kit is addressed. Stable — a reader stores it as the origin
 *        of what it installed, so renaming it forks the installation instead of
 *        updating it.
 * @param version human-facing version of the kit, or null. Shown, logged, and
 *        used as a fallback stamp; it is <b>not</b> what change detection reads.
 * @param revision what a reader compares to decide whether to fetch again.
 *        Change it exactly when the delivered bytes change. A content hash is
 *        the safe answer, which is what {@link StaticKitSource} computes; a
 *        build id works too, as long as it does not move while the content
 *        stands still — or stand still while the content moves.
 * @param description one sentence for a person deciding whether they want it
 */
public record OdeKitDeclaration(
        String id,
        @Nullable String version,
        String revision,
        @Nullable String description) {

    public OdeKitDeclaration {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("a kit declaration needs an id");
        }
        if (revision == null || revision.isBlank()) {
            // No fallback to the version on purpose. A reader that cannot tell
            // „changed" from „unchanged" would either refetch on every tick or
            // never — and guessing which of those to do is worse than saying
            // the declaration is incomplete.
            throw new IllegalArgumentException(
                    "kit '" + id + "' must declare a revision: it is what tells a reader"
                            + " whether the content changed");
        }
    }
}
