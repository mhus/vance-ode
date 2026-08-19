package de.mhus.vance.ode.core;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Data;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Everything Ode needs to reach a brain, bound from {@code vance.ode.*}.
 *
 * <p>Opt-in by presence: with no {@code base-url} configured, the
 * auto-configuration contributes nothing and the library is inert. A
 * dependency that starts talking to a server merely by being on the
 * classpath would be a bad neighbour in software we do not own.
 */
@ConfigurationProperties(prefix = "vance.ode")
@Data
public class VanceOdeProperties {

    /**
     * Brain base URL, e.g. {@code https://brain.example.com}. Empty
     * disables Ode entirely.
     */
    private String baseUrl = "";

    /** Tenant the calls are routed to. */
    private String tenant = "";

    /**
     * Default project for calls that do not name one. {@code _tenant} is
     * the conventional "no particular project" scope in Vancetope.
     */
    private String project = "_tenant";

    private Duration connectTimeout = Duration.ofSeconds(10);

    /**
     * Default per-request timeout. Deliberately generous: an event whose
     * script performs a model call answers in seconds, not milliseconds,
     * and that is the normal case rather than a pathology. Individual
     * events can override it.
     */
    private Duration requestTimeout = Duration.ofSeconds(60);

    /**
     * Events this application may fire, keyed by event name.
     *
     * <p>Declared rather than discovered: a caller has to name the event
     * in configuration before it can be triggered, so the set of things
     * this application can set off in a brain is readable from its own
     * config instead of from its code.
     */
    private Map<String, EventBinding> events = new LinkedHashMap<>();

    public boolean isConfigured() {
        return !baseUrl.isBlank() && !tenant.isBlank();
    }

    /** Per-event settings. */
    @Data
    public static class EventBinding {

        /**
         * Bearer token for this event. Events in Vancetope carry their own
         * token, so this is per event and not per connection.
         */
        private String token = "";

        /** Overrides {@link VanceOdeProperties#getProject()} for this event. */
        private @Nullable String project;

        /**
         * Overrides {@link VanceOdeProperties#getRequestTimeout()}. Worth
         * setting for events that run a model call.
         */
        private @Nullable Duration timeout;
    }
}
