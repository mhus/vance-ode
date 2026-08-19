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
