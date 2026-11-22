package io.github.itech_mm.apiro.http;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import io.github.itech_framework.api_client.interceptor.HeaderRedactor;
import io.github.itech_framework.api_client.utils.CancelToken;
import io.github.itech_mm.apiro.http.RequestPreparer.PreparedRequest;
import io.github.itech_mm.apiro.model.BodyType;
import io.github.itech_mm.apiro.model.FormPart;
import io.github.itech_mm.apiro.model.HttpMethod;
import io.github.itech_mm.apiro.model.KeyValue;
import io.github.itech_mm.apiro.model.RequestBody;
import io.github.itech_mm.apiro.model.RequestSettings;

class RequestExecutorTest {

    private record Captured(String method, String uri, Map<String, List<String>> headers, byte[] body) {
    }

    private static final List<KeyValue> NO_HEADERS = List.of();

    private static final AtomicReference<Captured> last = new AtomicReference<>();
    private static final AtomicInteger hits = new AtomicInteger();
    private static volatile CountDownLatch slowStarted = new CountDownLatch(1);

    private static ExecutorService pool;
    private static HttpServer server;
    private static String base;
    private static RequestExecutor executor;
    
    private static TestApiClients clients;

    @BeforeAll
    static void start() throws IOException {
        pool = Executors.newCachedThreadPool();
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.setExecutor(pool);
        server.createContext("/echo", exchange -> {
            capture(exchange);
            reply(exchange, 200, "application/json", "{\"ok\":true}".getBytes(StandardCharsets.UTF_8));
        });
        server.createContext("/missing", exchange -> {
            capture(exchange);
            reply(exchange, 404, "text/plain", "missing".getBytes(StandardCharsets.UTF_8));
        });
        server.createContext("/redirect", exchange -> {
            capture(exchange);
            exchange.getResponseHeaders().set("Location", base + "/echo");
            reply(exchange, 302, null, new byte[0]);
        });
        server.createContext("/cookie", exchange -> {
            capture(exchange);
            exchange.getResponseHeaders().add("Set-Cookie", "sid=abc; Path=/; HttpOnly");
            exchange.getResponseHeaders().add("Set-Cookie", "theme=dark");
            reply(exchange, 200, "text/plain", "ok".getBytes(StandardCharsets.UTF_8));
        });
        server.createContext("/latin", exchange -> {
            capture(exchange);
            reply(exchange, 200, "text/plain; charset=ISO-8859-1", "caf\u00e9".getBytes(StandardCharsets.ISO_8859_1));
        });
        server.createContext("/big", exchange -> {
            capture(exchange);
            byte[] data = new byte[4096];
            Arrays.fill(data, (byte) 'x');
            reply(exchange, 200, "text/plain", data);
        });
        server.createContext("/slow", exchange -> {
            slowStarted.countDown();
            try {
                Thread.sleep(2000);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
            }
            try {
                reply(exchange, 200, "text/plain", "late".getBytes(StandardCharsets.UTF_8));
            } catch (IOException ignored) {
                exchange.close();
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

    private static void capture(HttpExchange exchange) throws IOException {
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        exchange.getRequestHeaders().forEach((name, values) -> headers.put(name, List.copyOf(values)));
        byte[] body = exchange.getRequestBody().readAllBytes();
        hits.incrementAndGet();
        last.set(new Captured(exchange.getRequestMethod(), exchange.getRequestURI().toString(), headers, body));
    }

    private static void reply(HttpExchange exchange, int status, String contentType, byte[] body)
            throws IOException {
        if (contentType != null) {
            exchange.getResponseHeaders().set("Content-Type", contentType);
        }
        if (exchange.getRequestMethod().equals("HEAD") || body.length == 0) {
            exchange.sendResponseHeaders(status, -1);
        } else {
            exchange.sendResponseHeaders(status, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        }
        exchange.close();
    }

    private static PreparedRequest request(HttpMethod method, String url, List<KeyValue> headers, RequestBody body,
                                           RequestSettings settings) {
        return new PreparedRequest(method, url, headers, body, settings.withDefaults(), List.of());
    }

    private static PreparedRequest get(String path) {
        return request(HttpMethod.GET, base + path, NO_HEADERS, RequestBody.none(), RequestSettings.defaults());
    }

    private static List<KeyValue> headers(String... pairs) {
        List<KeyValue> result = new ArrayList<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            result.add(KeyValue.of(pairs[i], pairs[i + 1]));
        }
        return result;
    }

    private static ExchangeResult done(SendOutcome outcome) {
        assertInstanceOf(SendOutcome.Done.class, outcome, String.valueOf(outcome));
        return ((SendOutcome.Done) outcome).result();
    }

    private static void failed(SendOutcome outcome, FailureKind kind) {
        assertInstanceOf(SendOutcome.Failed.class, outcome, String.valueOf(outcome));
        assertEquals(kind, ((SendOutcome.Failed) outcome).kind(), String.valueOf(outcome));
    }

    private static String text(byte[] bytes) {
        return new String(bytes, StandardCharsets.ISO_8859_1);
    }

    @Test
    void getReturnsStatusBodyTimingAndKeepsTheQueryString() {
        ExchangeResult result = done(executor.send(get("/echo?x=1&y=a%20b"), new CancelToken()));
        assertEquals(200, result.response().status());
        assertEquals("OK", result.response().statusText());
        assertEquals("{\"ok\":true}", result.response().body());
        assertEquals(11, result.response().sizeBytes());
        assertEquals(1, result.response().attempts());
        assertTrue(result.response().totalMillis() >= result.response().headersMillis());
        assertEquals(base + "/echo?x=1&y=a%20b", result.sent().uri());
        assertEquals("/echo?x=1&y=a%20b", last.get().uri());
    }

    @ParameterizedTest
    @EnumSource(HttpMethod.class)
    void everyMethodIsSentAsChosen(HttpMethod method) {
        PreparedRequest prepared = request(method, base + "/echo", NO_HEADERS, RequestBody.none(),
                RequestSettings.defaults());
        ExchangeResult result = done(executor.send(prepared, new CancelToken()));
        assertEquals(200, result.response().status());
        assertEquals(method.name(), last.get().method());
        if (method == HttpMethod.HEAD) {
            assertEquals("", result.response().body());
        }
    }

    @Test
    void repeatedHeaderNamesAreAllSent() {
        PreparedRequest prepared = request(HttpMethod.GET, base + "/echo",
                headers("X-Tag", "a", "X-Tag", "b", "X-Trace", "t1"), RequestBody.none(), RequestSettings.defaults());
        done(executor.send(prepared, new CancelToken()));
        List<String> tags = new ArrayList<>();
        for (String value : last.get().headers().get("X-Tag")) {
            for (String piece : value.split(",")) {
                tags.add(piece.trim());
            }
        }
        assertTrue(tags.containsAll(List.of("a", "b")), tags.toString());
        assertEquals(List.of("t1"), last.get().headers().get("X-Trace"));
    }

    @Test
    void jsonTextIsSentAsWritten() {
        RequestBody body = RequestBody.of(BodyType.JSON, "{\"a\": 1,\n \"b\": [true]}");
        PreparedRequest prepared = request(HttpMethod.POST, base + "/echo", headers("Content-Type", "application/json"),
                body, RequestSettings.defaults());
        done(executor.send(prepared, new CancelToken()));
        assertEquals("{\"a\": 1,\n \"b\": [true]}", new String(last.get().body(), StandardCharsets.UTF_8));
        assertTrue(last.get().headers().get("Content-Type").get(0).startsWith("application/json"));
    }

    @Test
    void nonUtf8CharsetIsAddedToTheContentType() {
        RequestBody body = new RequestBody(BodyType.TEXT, "caf\u00e9", null, "ISO-8859-1", null, null, null);
        PreparedRequest prepared = request(HttpMethod.POST, base + "/echo", headers("Content-Type", "text/plain"), body,
                RequestSettings.defaults());
        done(executor.send(prepared, new CancelToken()));
        assertTrue(last.get().headers().get("Content-Type").get(0).toLowerCase().contains("charset=iso-8859-1"));
        assertArrayEquals("caf\u00e9".getBytes(StandardCharsets.ISO_8859_1), last.get().body());
    }

    @Test
    void formKeepsDuplicateKeysAndOrderAndSkipsDisabledFields() {
        RequestBody body = new RequestBody(BodyType.FORM, null, null, null,
                List.of(KeyValue.of("a", "1"), KeyValue.of("a", "2"), new KeyValue("skip", "x", false),
                        KeyValue.of("b", "3")),
                null, null);
        PreparedRequest prepared = request(HttpMethod.POST, base + "/echo",
                headers("Content-Type", "application/x-www-form-urlencoded"), body, RequestSettings.defaults());
        done(executor.send(prepared, new CancelToken()));
        assertEquals("a=1&a=2&b=3", new String(last.get().body(), StandardCharsets.UTF_8));
    }

    @Test
    void multipartSendsTextAndFileParts(@TempDir Path dir) throws IOException {
        Path file = Files.writeString(dir.resolve("notes.txt"), "hello file");
        RequestBody body = new RequestBody(BodyType.MULTIPART, null, null, null, null,
                List.of(FormPart.text("note", "hi there"), FormPart.file("upload", file.toString())), null);
        PreparedRequest prepared = request(HttpMethod.POST, base + "/echo", NO_HEADERS, body,
                RequestSettings.defaults());
        done(executor.send(prepared, new CancelToken()));
        String contentType = last.get().headers().get("Content-Type").get(0);
        assertTrue(contentType.startsWith("multipart/form-data"), contentType);
        assertTrue(contentType.contains("boundary="), contentType);
        String sent = text(last.get().body());
        assertTrue(sent.contains("name=\"note\""), sent);
        assertTrue(sent.contains("hi there"), sent);
        assertTrue(sent.contains("name=\"upload\""), sent);
        assertTrue(sent.contains("notes.txt"), sent);
        assertTrue(sent.contains("hello file"), sent);
    }

    @Test
    void binaryBodyIsStreamedFromDisk(@TempDir Path dir) throws IOException {
        byte[] data = {0, 1, 2, 3, (byte) 255, (byte) 128};
        Path file = Files.write(dir.resolve("blob.bin"), data);
        RequestBody body = new RequestBody(BodyType.BINARY, null, null, null, null, null, file.toString());
        PreparedRequest prepared = request(HttpMethod.PUT, base + "/echo",
                headers("Content-Type", "application/octet-stream"), body, RequestSettings.defaults());
        done(executor.send(prepared, new CancelToken()));
        assertArrayEquals(data, last.get().body());
        assertEquals("PUT", last.get().method());
    }

    @Test
    void errorStatusIsReturnedNotThrown() {
        ExchangeResult result = done(executor.send(get("/missing"), new CancelToken()));
        assertEquals(404, result.response().status());
        assertEquals("Not Found", result.response().statusText());
        assertEquals("missing", result.response().body());
    }

    @Test
    void redirectIsFollowedAndReported() {
        PreparedRequest prepared = request(HttpMethod.GET, base + "/redirect", NO_HEADERS, RequestBody.none(),
                new RequestSettings(null, null, true, 5, null, null));
        ExchangeResult result = done(executor.send(prepared, new CancelToken()));
        assertEquals(200, result.response().status());
        assertEquals(1, result.redirects().size());
        assertEquals(302, result.redirects().get(0).status());
        assertEquals("/echo", last.get().uri());
    }

    @Test
    void redirectCanBeSwitchedOff() {
        PreparedRequest prepared = request(HttpMethod.GET, base + "/redirect", NO_HEADERS, RequestBody.none(),
                new RequestSettings(null, null, false, null, null, null));
        ExchangeResult result = done(executor.send(prepared, new CancelToken()));
        assertEquals(302, result.response().status());
        assertTrue(result.redirects().isEmpty());
    }

    @Test
    void setCookieHeadersBecomeCookies() {
        ExchangeResult result = done(executor.send(get("/cookie"), new CancelToken()));
        List<KeyValue> cookies = result.response().cookies();
        assertEquals(2, cookies.size());
        assertTrue(cookies.contains(KeyValue.of("sid", "abc")), cookies.toString());
        assertTrue(cookies.contains(KeyValue.of("theme", "dark")), cookies.toString());
        long setCookieHeaders = result.response().headers().stream()
                .filter(header -> header.key().equalsIgnoreCase("Set-Cookie")).count();
        assertEquals(2, setCookieHeaders);
    }

    @Test
    void responseIsDecodedWithTheDeclaredCharset() {
        ExchangeResult result = done(executor.send(get("/latin"), new CancelToken()));
        assertEquals("caf\u00e9", result.response().body());
    }

    @Test
    void sentRequestKeepsTheRealAuthorizationAndRedactorMasksIt() {
        PreparedRequest prepared = request(HttpMethod.GET, base + "/echo",
                headers("Authorization", "Bearer secret-token"), RequestBody.none(), RequestSettings.defaults());
        ExchangeResult result = done(executor.send(prepared, new CancelToken()));
        assertEquals("Bearer secret-token", result.sent().header("Authorization").orElseThrow());
        Map<String, List<String>> masked = HeaderRedactor.defaults().redact(result.sent().headers());
        assertEquals(List.of("Bearer ***"), masked.get("Authorization"));
    }

    @Test
    void socketTimeoutIsAppliedPerCall() {
        PreparedRequest prepared = request(HttpMethod.GET, base + "/slow", NO_HEADERS, RequestBody.none(),
                new RequestSettings(null, 300, null, null, null, null));
        failed(executor.send(prepared, new CancelToken()), FailureKind.TIMEOUT);
    }

    @Test
    void cancelStopsTheCallInFlight() throws Exception {
        slowStarted = new CountDownLatch(1);
        CancelToken token = new CancelToken();
        CompletableFuture<SendOutcome> pending = CompletableFuture.supplyAsync(() -> executor.send(get("/slow"), token));
        assertTrue(slowStarted.await(5, TimeUnit.SECONDS));
        token.cancel();
        assertInstanceOf(SendOutcome.Cancelled.class, pending.get(5, TimeUnit.SECONDS));
    }

    @Test
    void aTokenCancelledBeforeSendingNeverSucceeds() {
        CancelToken token = new CancelToken();
        token.cancel();
        assertInstanceOf(SendOutcome.Cancelled.class, executor.send(get("/echo"), token));
    }

    @Test
    void refusedConnectionIsClassified() throws IOException {
        int port;
        try (ServerSocket socket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress())) {
            port = socket.getLocalPort();
        }
        PreparedRequest prepared = request(HttpMethod.GET, "http://127.0.0.1:" + port + "/", NO_HEADERS,
                RequestBody.none(), RequestSettings.defaults());
        failed(executor.send(prepared, new CancelToken()), FailureKind.CONNECTION);
    }

    @Test
    void unknownHostIsClassified() {
        PreparedRequest prepared = request(HttpMethod.GET, "http://no-such-host.invalid/", NO_HEADERS,
                RequestBody.none(), RequestSettings.defaults());
        failed(executor.send(prepared, new CancelToken()), FailureKind.UNKNOWN_HOST);
    }

    @Test
    void oversizedResponseIsReportedAsTooLarge() {
        try (TestApiClients small = new TestApiClients(1024)) {
            RequestExecutor limited = new RequestExecutor(small.calls());
            failed(limited.send(get("/big"), new CancelToken()), FailureKind.BODY_TOO_LARGE);
        }
    }

    @Test
    void missingFileFailsBeforeAnythingIsSent(@TempDir Path dir) {
        int before = hits.get();
        RequestBody body = new RequestBody(BodyType.BINARY, null, null, null, null, null,
                dir.resolve("nope.bin").toString());
        PreparedRequest prepared = request(HttpMethod.POST, base + "/echo", NO_HEADERS, body,
                RequestSettings.defaults());
        failed(executor.send(prepared, new CancelToken()), FailureKind.FILE_MISSING);
        assertEquals(before, hits.get());
    }

    @Test
    void blankFilePathIsReportedAsNoFileSelected() {
        RequestBody body = new RequestBody(BodyType.BINARY, null, null, null, null, null, "");
        PreparedRequest prepared = request(HttpMethod.POST, base + "/echo", NO_HEADERS, body,
                RequestSettings.defaults());
        SendOutcome outcome = executor.send(prepared, new CancelToken());
        failed(outcome, FailureKind.FILE_MISSING);
        assertEquals("No file selected", ((SendOutcome.Failed) outcome).message());
    }

    @Test
    void urlWithoutSchemeIsRejected() {
        PreparedRequest prepared = request(HttpMethod.GET, "127.0.0.1:8080/echo", NO_HEADERS, RequestBody.none(),
                RequestSettings.defaults());
        failed(executor.send(prepared, new CancelToken()), FailureKind.INVALID_REQUEST);
        assertFalse(prepared.url().startsWith("http"));
    }
}