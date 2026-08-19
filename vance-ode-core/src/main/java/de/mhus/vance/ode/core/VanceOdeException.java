package de.mhus.vance.ode.core;

import lombok.Getter;
import org.jspecify.annotations.Nullable;

/**
 * Anything that stopped a call to the brain from succeeding.
 *
 * <p>One exception type with a {@link #getKind() kind}, rather than a
 * hierarchy: the caller's decision is nearly always the same three-way
 * split — retry, fix the configuration, or give up — and a type per HTTP
 * status would make that a chain of instanceof checks. {@link #isRetryable()}
 * answers the question that actually gets asked.
 */
@Getter
public class VanceOdeException extends RuntimeException {

    /** What went wrong, coarse enough to switch on. */
    public enum Kind {

        /** Ode is not configured, or the event was never declared. */
        CONFIGURATION,

        /** Rejected bearer token — 401. */
        UNAUTHORIZED,

        /** No such event, or it is disabled — 404. */
        NOT_FOUND,

        /** The brain accepted the call and the action itself failed — 5xx. */
        REMOTE_FAILURE,

        /** Connection refused, DNS, TLS, timeout — the brain was not reached. */
        TRANSPORT,

        /** A 2xx arrived but was not the shape we expect. */
        PROTOCOL
    }

    private final Kind kind;

    /** HTTP status when there was one, {@code 0} otherwise. */
    private final int status;

    public VanceOdeException(Kind kind, int status, String message) {
        this(kind, status, message, null);
    }

    public VanceOdeException(Kind kind, int status, String message, @Nullable Throwable cause) {
        super(message, cause);
        this.kind = kind;
        this.status = status;
    }

    /**
     * Whether trying the same call again could plausibly succeed.
     *
     * <p>Transport failures and remote failures can be transient. A
     * rejected token, an unknown event and a malformed response cannot —
     * retrying those just repeats the same mistake more often, which for
     * an event that spends model tokens is worse than doing nothing.
     */
    public boolean isRetryable() {
        return kind == Kind.TRANSPORT || kind == Kind.REMOTE_FAILURE;
    }

    static VanceOdeException configuration(String message) {
        return new VanceOdeException(Kind.CONFIGURATION, 0, message);
    }

    static VanceOdeException transport(String message, @Nullable Throwable cause) {
        return new VanceOdeException(Kind.TRANSPORT, 0, message, cause);
    }
}
