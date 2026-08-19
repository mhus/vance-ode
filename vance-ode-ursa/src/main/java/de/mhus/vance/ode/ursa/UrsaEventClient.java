package de.mhus.vance.ode.ursa;

import de.mhus.vance.ode.core.OdeHttpTransport;
import de.mhus.vance.ode.core.VanceOdeException;
import de.mhus.vance.ode.core.VanceOdeProperties;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;

/**
 * Fires Vancetope events.
 *
 * <p>An event has to be declared under {@code vance.ode.events.<name>}
 * before it can be fired. That is a deliberate restriction rather than a
 * limitation of the transport: it keeps the set of things this
 * application can set off in a brain readable from its own configuration,
 * instead of scattered across whatever strings its code happens to pass.
 */
public class UrsaEventClient {

    private static final Logger log = LoggerFactory.getLogger(UrsaEventClient.class);

    private final OdeHttpTransport transport;
    private final VanceOdeProperties properties;

    public UrsaEventClient(OdeHttpTransport transport) {
        this.transport = transport;
        this.properties = transport.properties();
    }

    /**
     * Fires {@code eventName} and returns what came back.
     *
     * @param payload serialised as the request body; the brain exposes it
     *                to the event's target under {@code params.payload}
     * @throws VanceOdeException when the event is not configured, the
     *         token is rejected, the brain is unreachable, or the action
     *         itself failed
     */
    public EventResult fire(String eventName, @Nullable Object payload) {
        VanceOdeProperties.EventBinding binding = bindingFor(eventName);
        String project = binding.getProject() == null || binding.getProject().isBlank()
                ? properties.getProject()
                : binding.getProject();
        Duration timeout = binding.getTimeout() == null
                ? properties.getRequestTimeout()
                : binding.getTimeout();

        String path = "/brain/" + encode(properties.getTenant())
                + "/event/" + encode(project)
                + "/" + encode(eventName);

        long startedAt = System.currentTimeMillis();
        JsonNode reply = transport.postJson(path, binding.getToken(), payload, timeout);
        EventResult result = toResult(eventName, reply);

        log.debug("Ode: fired event '{}' on {}/{} in {} ms — {}",
                eventName, properties.getTenant(), project,
                System.currentTimeMillis() - startedAt,
                result.hasOutput() ? "returned output" : "dispatched");
        return result;
    }

    /**
     * Fires an event and returns its result as text.
     *
     * <p>The shape almost every synchronous event has: a script returns a
     * string, the brain wraps it as {@code {"value": …}}, and the caller
     * wants the string. Empty when the event produced no output.
     */
    public Optional<String> fireForText(String eventName, @Nullable Object payload) {
        return fire(eventName, payload).text();
    }

    /**
     * Fires an event and insists on a text result.
     *
     * <p>For call sites where "no output" is a configuration mistake
     * rather than an outcome — an event declared as a translator that
     * turns out to be {@code async: true} would otherwise silently return
     * nothing and be discovered much later.
     */
    public String requireText(String eventName, @Nullable Object payload) {
        EventResult result = fire(eventName, payload);
        return result.text().orElseThrow(() -> new VanceOdeException(
                VanceOdeException.Kind.PROTOCOL, 0,
                "event '" + eventName + "' returned no text result"
                        + (result.getRunId() != null
                        ? " — it is a spawn (runId " + result.getRunId() + "), not a synchronous"
                        + " script"
                        : " — it may be async: true, or withhold its output")));
    }

    /** Event names this client may fire, as declared in configuration. */
    public Set<String> declaredEvents() {
        return Set.copyOf(properties.getEvents().keySet());
    }

    private VanceOdeProperties.EventBinding bindingFor(String eventName) {
        VanceOdeProperties.EventBinding binding = properties.getEvents().get(eventName);
        if (binding == null) {
            throw new VanceOdeException(VanceOdeException.Kind.CONFIGURATION, 0,
                    "event '" + eventName + "' is not declared — add it under "
                            + "vance.ode.events." + eventName
                            + " (declared: " + declaredEvents() + ")");
        }
        return binding;
    }

    private static EventResult toResult(String eventName, JsonNode reply) {
        return EventResult.builder()
                .event(text(reply, "event", eventName))
                // Mapped from the wire's historical names — see EventResult.
                .target(text(reply, "workflowName", ""))
                .runId(textOrNull(reply, "workflowRunId"))
                .output(objectOrNull(reply.get("output")))
                .build();
    }

    private static String text(JsonNode node, String field, String fallback) {
        String value = textOrNull(node, field);
        return value == null ? fallback : value;
    }

    private static @Nullable String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isValueNode()) {
            return null;
        }
        String text = value.stringValue();
        return text.isBlank() ? null : text;
    }

    /**
     * Converts the {@code output} node to a plain map.
     *
     * <p>Plain {@code Object} values rather than {@code JsonNode}: the
     * caller is application code that wants a string or a number, and
     * handing it a Jackson tree would make the library's JSON choice part
     * of its API.
     */
    private static @Nullable Map<String, Object> objectOrNull(@Nullable JsonNode node) {
        if (node == null || !node.isObject() || node.isEmpty()) {
            return null;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        node.propertyStream().forEach(entry -> out.put(entry.getKey(), unwrap(entry.getValue())));
        return out;
    }

    private static @Nullable Object unwrap(JsonNode node) {
        if (node.isNull()) return null;
        if (node.isTextual()) return node.stringValue();
        if (node.isBoolean()) return node.booleanValue();
        if (node.isInt() || node.isLong()) return node.longValue();
        if (node.isNumber()) return node.doubleValue();
        if (node.isArray()) {
            return node.valueStream().map(UrsaEventClient::unwrap).toList();
        }
        if (node.isObject()) {
            Map<String, Object> nested = new LinkedHashMap<>();
            node.propertyStream().forEach(e -> nested.put(e.getKey(), unwrap(e.getValue())));
            return nested;
        }
        return node.toString();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
