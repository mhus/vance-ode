/**
 * Firing Vancetope events from outside.
 *
 * <p>The event endpoint is the least demanding way into a brain: a plain
 * HTTP POST with a bearer token, no JWT, no service account, no token
 * refresh. What the event does — spawn a worker, run a script, call a
 * model — is configured on the brain side and invisible here, which is
 * the property that makes this the right integration surface: the caller
 * names a capability, not an implementation.
 *
 * <p>A synchronous event returns its result in the same response; see
 * {@link de.mhus.vance.ode.ursa.EventResult}.
 */
@NullMarked
package de.mhus.vance.ode.ursa;

import org.jspecify.annotations.NullMarked;
