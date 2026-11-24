package io.github.itech_mm.apiro.service.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.itech_framework.api_client.utils.CancelToken;
import io.github.itech_mm.apiro.model.Assertion;
import io.github.itech_mm.apiro.model.BodyType;
import io.github.itech_mm.apiro.model.Extractor;
import io.github.itech_mm.apiro.model.HttpMethod;
import io.github.itech_mm.apiro.model.KeyValue;
import io.github.itech_mm.apiro.model.OnFailure;
import io.github.itech_mm.apiro.model.Project;
import io.github.itech_mm.apiro.model.RequestBody;
import io.github.itech_mm.apiro.model.RequestItem;
import io.github.itech_mm.apiro.model.ScenarioItem;
import io.github.itech_mm.apiro.model.Step;
import io.github.itech_mm.apiro.service.scenario.ScenarioResult;
import io.github.itech_mm.apiro.service.scenario.ScenarioRunner;
import io.github.itech_mm.apiro.service.scenario.StepResult;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ScenarioRunnerHttpTest {

    private static ExecutorService pool;
    private static HttpServer server;
    private static String base;
    private static TestApiClients clients;
    private static RequestExecutor executor;

    @BeforeAll
    static void start() throws IOException {
        pool = Executors.newCachedThreadPool();
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.setExecutor(pool);
        server.createContext("/register", exchange -> reply(exchange, 201, "{\"id\":\"u1\"}"));
        server.createContext("/login", exchange -> reply(exchange, 200, "{\"data\":{\"token\":\"t-123\"}}"));
        server.createContext("/orders", exchange -> {
            String auth = exchange.getRequestHeaders().getFirst("Authorization");
            if (!"Bearer t-123".equals(auth)) {
                reply(exchange, 401, "{\"error\":\"unauthorized\"}");
            } else if (exchange.getRequestMethod().equals("POST")) {
                reply(exchange, 201, "{\"id\":\"o1\"}");
            } else if (exchange.getRequestURI().getPath().equals("/orders/o1")) {
                reply(exchange, 200, "{\"id\":\"o1\",\"state\":\"CONFIRMED\"}");
            } else {
                reply(exchange, 404, "{\"error\":\"missing\"}");
            }
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
        clients = new TestApiClients(50L * 1024 * 1024);
        executor = new RequestExecutor(clients.calls());
    }

    @AfterAll
    static void stop() {
        clients.close();
        server.stop(0);
        pool.shutdownNow();
    }

    private static void reply(HttpExchange exchange, int status, String body) throws IOException {
        exchange.getRequestBody().readAllBytes();
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static RequestItem post(String id, String path, List<KeyValue> headers) {
        return new RequestItem(id, id, HttpMethod.POST, base + path, null, headers, null,
                RequestBody.of(BodyType.JSON, "{}"), null);
    }

    private static RequestItem get(String id, String path, List<KeyValue> headers) {
        return new RequestItem(id, id, HttpMethod.GET, base + path, null, headers, null, null, null);
    }

    private static Project project() {
        List<KeyValue> auth = List.of(KeyValue.of("Authorization", "Bearer ${token}"));
        return new Project(null, 0, "p", "Shop", null, null, null, null, null, null,
                List.of(post("register", "/register", null), post("login", "/login", null),
                        post("create", "/orders", auth), get("fetch", "/orders/${orderId}", auth)));
    }

    private static Step step(String id, String requestId, List<Extractor> extractors, List<Assertion> assertions) {
        return new Step(id, requestId, id, extractors, assertions, 0, null, 0, 0);
    }

    private static Assertion status(String expected) {
        return new Assertion(Assertion.Source.STATUS, "", Assertion.Operator.EQUALS, expected);
    }

    private static ScenarioItem flow(String expectedState) {
        return new ScenarioItem("sc", "Checkout", null, List.of(
                step("s1", "register", List.of(new Extractor("userId", Extractor.Source.JSON_PATH, "$.id")),
                        List.of(status("201"))),
                step("s2", "login", List.of(new Extractor("token", Extractor.Source.JSON_PATH, "$.data.token")),
                        List.of(status("200"))),
                step("s3", "create", List.of(new Extractor("orderId", Extractor.Source.JSON_PATH, "$.id")),
                        List.of(status("201"))),
                step("s4", "fetch", null, List.of(new Assertion(Assertion.Source.JSON_PATH, "$.state",
                        Assertion.Operator.EQUALS, expectedState)))), null, OnFailure.STOP);
    }

    @Test
    void registerLoginCreateAndFetchPassesOverRealHttp() {
        ScenarioResult result = new ScenarioRunner(executor::send).run(project(), "", flow("CONFIRMED"),
                new CancelToken());

        assertEquals(ScenarioResult.State.PASSED, result.state(), result.message());
        assertEquals(4, result.count(StepResult.State.PASSED));
        assertTrue(result.steps().get(3).exchange().response().body().contains("CONFIRMED"));
    }

    @Test
    void wrongExpectedValueFailsTheLastStep() {
        ScenarioResult result = new ScenarioRunner(executor::send).run(project(), "", flow("SHIPPED"),
                new CancelToken());

        assertEquals(ScenarioResult.State.FAILED, result.state());
        assertEquals(3, result.count(StepResult.State.PASSED));
        assertEquals(StepResult.State.FAILED, result.steps().get(3).state());
    }

    @Test
    void skippingLoginLeavesTheTokenUnresolved() {
        ScenarioItem withoutLogin = new ScenarioItem("sc", "No login", null, List.of(
                step("s1", "create", null, List.of(status("201")))), null, OnFailure.STOP);

        ScenarioResult result = new ScenarioRunner(executor::send).run(project(), "", withoutLogin,
                new CancelToken());

        assertEquals(ScenarioResult.State.FAILED, result.state());
        assertTrue(result.steps().get(0).message().contains("token"));
    }
}
