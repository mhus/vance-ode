package de.mhus.vance.ode.ursa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import de.mhus.vance.ode.core.OdeHttpTransport;
import de.mhus.vance.ode.core.VanceOdeException;
import de.mhus.vance.ode.core.VanceOdeProperties;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Drives the client against a real HTTP server rather than a mocked
 * transport.
 *
 * <p>The interesting failures of an HTTP client — a status mapped to the
 * wrong kind, a header that never gets sent, a body that is not the shape
 * the parser assumes — all live below the point a mock would stub out.
 * The JDK's built-in server costs nothing and keeps them in scope.
 */
class UrsaEventClientTest {

    private HttpServer server;
    private UrsaEventClient client;

    /** Requests the fake brain received, for asserting what was sent. */
    private final List<Recorded> received = new CopyOnWriteArrayList<>();

    private record Recorded(String path, String authorization, String body) {}

    /** Response the next request will get. */
    private int status = 200;
    private String responseBody = "{}";

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String body;
            try (InputStream in = exchange.getRequestBody()) {
                body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            received.add(new Recorded(
                    exchange.getRequestURI().getPath(),
                    exchange.getRequestHeaders().getFirst("Authorization"),
                    body));
            byte[] out = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });
        server.start();

        VanceOdeProperties properties = new VanceOdeProperties();
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setTenant("acme");
        properties.setProject("giant-slingshot");
        properties.setRequestTimeout(Duration.ofSeconds(10));

        VanceOdeProperties.EventBinding translate = new VanceOdeProperties.EventBinding();
        translate.setToken("translate-secret");
        properties.getEvents().put("translate-article", translate);

        client = new UrsaEventClient(new OdeHttpTransport(properties));
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    // ─── the synchronous case ───

    @Test
    void fire_returns_the_events_output() {
        responseBody = """
                {"event":"translate-article",
                 "workflowName":"script:_vance/scripts/translate-article.js",
                 "output":{"value":"Der Rat hat zugestimmt."}}
                """;

        EventResult result = client.fire("translate-article", Map.of("text", "The council agreed."));

        assertThat(result.getEvent()).isEqualTo("translate-article");
        assertThat(result.getTarget()).isEqualTo("script:_vance/scripts/translate-article.js");
        assertThat(result.getRunId()).isNull();
        assertThat(result.text()).contains("Der Rat hat zugestimmt.");
        assertThat(result.hasOutput()).isTrue();
    }

    @Test
    void fire_sends_the_bearer_token_and_the_payload() {
        responseBody = "{\"event\":\"translate-article\",\"output\":{\"value\":\"x\"}}";

        client.fire("translate-article", Map.of("text", "hello", "targetLang", "de"));

        assertThat(received).hasSize(1);
        Recorded request = received.getFirst();
        assertThat(request.path()).isEqualTo("/brain/acme/event/giant-slingshot/translate-article");
        assertThat(request.authorization()).isEqualTo("Bearer translate-secret");
        assertThat(request.body()).contains("\"text\":\"hello\"").contains("\"targetLang\":\"de\"");
    }

    @Test
    void fireForText_unwraps_the_value_convention() {
        // A scalar return arrives under `value` — that is the brain's
        // mapping, and knowing it so the caller does not have to is the
        // point of the convenience method.
        responseBody = "{\"event\":\"e\",\"output\":{\"value\":\"translated\"}}";

        assertThat(client.fireForText("translate-article", null)).contains("translated");
    }

    @Test
    void spawn_response_yields_a_runId_and_no_output() {
        responseBody = "{\"event\":\"e\",\"workflowName\":\"w\",\"workflowRunId\":\"run-123\"}";

        EventResult result = client.fire("translate-article", null);

        assertThat(result.getRunId()).isEqualTo("run-123");
        assertThat(result.hasOutput()).isFalse();
        assertThat(result.text()).isEmpty();
    }

    @Test
    void requireText_names_the_reason_when_the_event_is_a_spawn() {
        // "No output" from an event declared as a translator is a
        // configuration mistake, and it should say which one.
        responseBody = "{\"event\":\"e\",\"workflowName\":\"w\",\"workflowRunId\":\"run-9\"}";

        assertThatThrownBy(() -> client.requireText("translate-article", null))
                .isInstanceOf(VanceOdeException.class)
                .hasMessageContaining("spawn")
                .hasMessageContaining("run-9");
    }

    @Test
    void non_string_output_is_not_forced_into_text() {
        responseBody = "{\"event\":\"e\",\"output\":{\"count\":3}}";

        EventResult result = client.fire("translate-article", null);

        assertThat(result.hasOutput()).isTrue();
        assertThat(result.getOutput()).containsEntry("count", 3L);
        assertThat(result.text()).isEmpty();
    }

    // ─── failure mapping ───

    @Test
    void undeclared_event_fails_before_any_request() {
        // The set of events an application may fire is readable from its
        // configuration; firing an undeclared one is a config error, not a
        // 404 from the far end.
        assertThatThrownBy(() -> client.fire("not-declared", null))
                .isInstanceOf(VanceOdeException.class)
                .satisfies(e -> assertThat(((VanceOdeException) e).getKind())
                        .isEqualTo(VanceOdeException.Kind.CONFIGURATION))
                .hasMessageContaining("vance.ode.events.not-declared");
        assertThat(received).isEmpty();
    }

    @Test
    void rejected_token_maps_to_unauthorized_and_is_not_retryable() {
        status = 401;
        responseBody = "{\"message\":\"Invalid or missing bearer token\"}";

        assertThatThrownBy(() -> client.fire("translate-article", null))
                .isInstanceOf(VanceOdeException.class)
                .satisfies(e -> {
                    VanceOdeException ex = (VanceOdeException) e;
                    assertThat(ex.getKind()).isEqualTo(VanceOdeException.Kind.UNAUTHORIZED);
                    assertThat(ex.isRetryable()).isFalse();
                })
                // The brain's own message is the useful part.
                .hasMessageContaining("Invalid or missing bearer token");
    }

    @Test
    void unknown_event_maps_to_not_found() {
        status = 404;
        responseBody = "{\"message\":\"Event 'x' not found\"}";

        assertThatThrownBy(() -> client.fire("translate-article", null))
                .satisfies(e -> assertThat(((VanceOdeException) e).getKind())
                        .isEqualTo(VanceOdeException.Kind.NOT_FOUND));
    }

    @Test
    void a_failed_action_maps_to_remote_failure_and_is_retryable() {
        status = 502;
        responseBody = "{\"message\":\"Event execution failed: Script raised\"}";

        assertThatThrownBy(() -> client.fire("translate-article", null))
                .satisfies(e -> {
                    VanceOdeException ex = (VanceOdeException) e;
                    assertThat(ex.getKind()).isEqualTo(VanceOdeException.Kind.REMOTE_FAILURE);
                    assertThat(ex.isRetryable()).isTrue();
                    assertThat(ex.getStatus()).isEqualTo(502);
                });
    }

    @Test
    void a_non_json_success_body_is_a_protocol_error() {
        responseBody = "<html>proxy error page</html>";

        assertThatThrownBy(() -> client.fire("translate-article", null))
                .satisfies(e -> assertThat(((VanceOdeException) e).getKind())
                        .isEqualTo(VanceOdeException.Kind.PROTOCOL));
    }

    @Test
    void unreachable_brain_is_a_transport_error_and_is_retryable() {
        VanceOdeProperties properties = new VanceOdeProperties();
        properties.setBaseUrl("http://127.0.0.1:1");
        properties.setTenant("acme");
        properties.setConnectTimeout(Duration.ofMillis(200));
        properties.setRequestTimeout(Duration.ofMillis(500));
        VanceOdeProperties.EventBinding binding = new VanceOdeProperties.EventBinding();
        binding.setToken("t");
        properties.getEvents().put("e", binding);

        UrsaEventClient offline = new UrsaEventClient(new OdeHttpTransport(properties));

        assertThatThrownBy(() -> offline.fire("e", null))
                .satisfies(e -> {
                    VanceOdeException ex = (VanceOdeException) e;
                    assertThat(ex.getKind()).isEqualTo(VanceOdeException.Kind.TRANSPORT);
                    assertThat(ex.isRetryable()).isTrue();
                });
    }

    @Test
    void unconfigured_ode_refuses_rather_than_guessing_a_url() {
        VanceOdeProperties empty = new VanceOdeProperties();
        VanceOdeProperties.EventBinding binding = new VanceOdeProperties.EventBinding();
        binding.setToken("t");
        empty.getEvents().put("e", binding);

        UrsaEventClient unconfigured = new UrsaEventClient(new OdeHttpTransport(empty));

        assertThatThrownBy(() -> unconfigured.fire("e", null))
                .satisfies(e -> assertThat(((VanceOdeException) e).getKind())
                        .isEqualTo(VanceOdeException.Kind.CONFIGURATION));
    }

    // ─── per-event overrides ───

    @Test
    void an_event_can_override_the_project() {
        VanceOdeProperties.EventBinding binding = new VanceOdeProperties.EventBinding();
        binding.setToken("t");
        binding.setProject("_tenant");
        // Rebuild against the same server with the override in place.
        VanceOdeProperties properties = new VanceOdeProperties();
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setTenant("acme");
        properties.setProject("giant-slingshot");
        properties.getEvents().put("elsewhere", binding);
        responseBody = "{\"event\":\"elsewhere\"}";

        new UrsaEventClient(new OdeHttpTransport(properties)).fire("elsewhere", null);

        assertThat(received.getFirst().path()).isEqualTo("/brain/acme/event/_tenant/elsewhere");
    }

    @Test
    void declaredEvents_reports_what_may_be_fired() {
        assertThat(client.declaredEvents()).containsExactly("translate-article");
    }
}
