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
 * Shared foundation for both directions of the Vancetope binding:
 * connection configuration, the error model, and the HTTP transport.
 *
 * <p>Free of Spring Web on purpose. A consumer that only calls out to a
 * brain — a collector, a batch job, a CLI — should not inherit a servlet
 * container because the library also happens to offer inbound endpoints.
 */
@NullMarked
package de.mhus.vance.ode.core;

import org.jspecify.annotations.NullMarked;
