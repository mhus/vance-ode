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
/**
 * The little that every <b>inbound</b> subsystem needs: a shared-secret guard, a
 * path, and one error shape.
 *
 * <p>This is the one place in {@code vance-ode-core} that touches Spring Web,
 * and it is deliberate. The README's rule is that no <b>controller</b> goes in
 * here — that is what would drag a servlet stack into the outbound-only case.
 * A {@code HandlerInterceptor} and a properties interface do not: both
 * dependencies are {@code provided}, and provided scope is not transitive, so an
 * application that only fires events still inherits nothing.
 *
 * <p>The alternative was a second copy per inbound module. The sibling repo made
 * the same call for the same reason ({@code vance-shared} carries one shared
 * filter base with provided-scoped Spring Web and nothing else web-shaped).
 *
 * <p>{@code @NullMarked} package — references are non-null by default unless
 * annotated {@code @Nullable}.
 */
@NullMarked
package de.mhus.vance.ode.inbound;

import org.jspecify.annotations.NullMarked;
