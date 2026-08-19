package de.mhus.vance.ode.core;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * The one place Ode speaks HTTP to a brain.
 *
 * <p>Every connector goes through here, which is what makes the timeout,
 * the error mapping and the proxy behaviour one thing rather than a
 * convention each connector re-implements.
 *
 * <p>Failures are thrown, not returned — the opposite of how a crawler
 * would do it. A call into the brain is a deliberate act by application
 * code with a caller who wants an answer, not a sweep over thousands of
 * unreliable hosts, so an exception is the honest signal.
 */
public class OdeHttpTransport {

    private static final Logger log = LoggerFactory.getLogger(OdeHttpTransport.class);

    /** How much of an error body is quoted into the exception message. */
    private static final int ERROR_EXCERPT_CHARS = 500;

    private final HttpClient client;
    private final ObjectMapper json;
    private final VanceOdeProperties properties;

    public OdeHttpTransport(VanceOdeProperties properties) {
        this.properties = properties;
        this.json = JsonMapper.builder().build();
        this.client = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * POSTs {@code body} as JSON and parses the JSON reply.
     *
     * @param path        brain-absolute path, starting with {@code /}
     * @param bearerToken sent as {@code Authorization: Bearer …}; skipped when blank
     * @param body        serialised as the request body; {@code null} sends {@code {}}
     * @param timeout     per-request timeout
     * @throws VanceOdeException on any non-2xx, transport failure or unparseable reply
     */
    public JsonNode postJson(String path, @Nullable String bearerToken,
            @Nullable Object body, Duration timeout) {

        if (!properties.isConfigured()) {
            throw VanceOdeException.configuration(
                    "vance.ode.base-url and vance.ode.tenant must be set before calling a brain");
        }
        URI uri = URI.create(trimTrailingSlash(properties.getBaseUrl()) + path);

        String payload;
        try {
            payload = body == null ? "{}" : json.writeValueAsString(body);
        } catch (RuntimeException e) {
            throw new VanceOdeException(VanceOdeException.Kind.PROTOCOL, 0,
                    "request body is not serialisable: " + e.getMessage(), e);
        }

        HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8));
        if (bearerToken != null && !bearerToken.isBlank()) {
            request.header("Authorization", "Bearer " + bearerToken);
        }

        HttpResponse<String> response;
        try {
            response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw VanceOdeException.transport(
                    "could not reach " + uri + ": " + e.getClass().getSimpleName()
                            + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw VanceOdeException.transport("interrupted while calling " + uri, e);
        }

        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            throw statusException(uri, status, response.body());
        }

        try {
            return json.readTree(response.body());
        } catch (RuntimeException e) {
            // Jackson 3 throws unchecked.
            throw new VanceOdeException(VanceOdeException.Kind.PROTOCOL, status,
                    "brain replied " + status + " with a body that is not JSON: "
                            + excerpt(response.body()), e);
        }
    }

    /**
     * Maps a status onto a {@link VanceOdeException.Kind}.
     *
     * <p>The brain's error bodies carry a {@code message} field; quoting
     * it matters because the useful part is usually in there — "Event
     * 'x' not found" tells an operator what to fix, while "HTTP 404"
     * does not.
     */
    private VanceOdeException statusException(URI uri, int status, @Nullable String body) {
        String reason = extractMessage(body);
        VanceOdeException.Kind kind = switch (status) {
            case 401, 403 -> VanceOdeException.Kind.UNAUTHORIZED;
            case 404 -> VanceOdeException.Kind.NOT_FOUND;
            default -> status >= 500
                    ? VanceOdeException.Kind.REMOTE_FAILURE
                    : VanceOdeException.Kind.PROTOCOL;
        };
        log.debug("Ode: {} returned {} — {}", uri, status, reason);
        return new VanceOdeException(kind, status,
                "brain returned " + status + " for " + uri + ": " + reason);
    }

    private String extractMessage(@Nullable String body) {
        if (body == null || body.isBlank()) {
            return "(empty body)";
        }
        try {
            JsonNode node = json.readTree(body);
            JsonNode message = node.get("message");
            if (message != null && message.isValueNode() && !message.stringValue().isBlank()) {
                return message.stringValue();
            }
        } catch (RuntimeException e) {
            // Not JSON — fall through to the raw excerpt.
        }
        return excerpt(body);
    }

    private static String excerpt(@Nullable String body) {
        if (body == null || body.isBlank()) {
            return "(empty body)";
        }
        String flat = body.replaceAll("\\s+", " ").trim();
        return flat.length() <= ERROR_EXCERPT_CHARS
                ? flat
                : flat.substring(0, ERROR_EXCERPT_CHARS) + "…";
    }

    private static String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    /** Configuration this transport was built with — connectors read tenant/project from it. */
    public VanceOdeProperties properties() {
        return properties;
    }
}
