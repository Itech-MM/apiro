package io.github.itech_mm.apiro.service.manifest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.itech_mm.apiro.spec.Manifest;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ManifestFetcherTest {

    private final List<HttpServer> servers = new ArrayList<>();

    private final List<String> tokens = new ArrayList<>();

    @AfterEach
    void stop() {
        servers.forEach(server -> server.stop(0));
    }

    @Test
    void derivesTheContextPathFromTheAddress() throws IOException {
        HttpServer server = server();
        serve(server, "/shop/apiro/manifest", manifest(""));

        Manifest manifest = ManifestFetcher.fetch(address(server, "/shop/apiro/manifest?x=1"), null);

        assertEquals("/shop", manifest.getApplication().getContextPath());
    }

    @Test
    void removesTheServletPathFromTheDerivedContextPath() throws IOException {
        HttpServer server = server();
        serve(server, "/shop/api/apiro/manifest", manifest("\"application\":{\"servletPath\":\"/api\"},"));

        Manifest manifest = ManifestFetcher.fetch(address(server, "/shop/api/apiro/manifest"), null);

        assertEquals("/shop", manifest.getApplication().getContextPath());
    }

    @Test
    void keepsTheDeclaredContextPath() throws IOException {
        HttpServer server = server();
        serve(server, "/proxy/apiro/manifest", manifest("\"application\":{\"contextPath\":\"/shop\"},"));

        Manifest manifest = ManifestFetcher.fetch(address(server, "/proxy/apiro/manifest"), null);

        assertEquals("/shop", manifest.getApplication().getContextPath());
    }

    @Test
    void derivesNothingAtTheRootOrFromAnotherPath() throws IOException {
        HttpServer server = server();
        serve(server, "/apiro/manifest", manifest(""));
        serve(server, "/files/orders.json", manifest(""));

        assertNull(ManifestFetcher.fetch(address(server, "/apiro/manifest"), null).getApplication());
        assertNull(ManifestFetcher.fetch(address(server, "/files/orders.json"), null).getApplication());
    }

    @Test
    void sendsTheTokenAlongARedirectOnTheSameOrigin() throws IOException {
        HttpServer server = server();
        redirect(server, "/old", "/shop/apiro/manifest");
        serve(server, "/shop/apiro/manifest", manifest(""));

        Manifest manifest = ManifestFetcher.fetch(address(server, "/old"), "secret");

        assertEquals(Arrays.asList("secret", "secret"), tokens);
        assertEquals("/shop", manifest.getApplication().getContextPath());
    }

    @Test
    void withholdsTheTokenFromAnotherOrigin() throws IOException {
        HttpServer first = server();
        HttpServer second = server();
        redirect(first, "/apiro/manifest", address(second, "/apiro/manifest"));
        serve(second, "/apiro/manifest", manifest(""));

        ManifestFetcher.fetch(address(first, "/apiro/manifest"), "secret");

        assertEquals(Arrays.asList("secret", null), tokens);
    }

    @Test
    void explainsARefusalAfterTheTokenWasWithheld() throws IOException {
        HttpServer first = server();
        HttpServer second = server();
        redirect(first, "/apiro/manifest", address(second, "/apiro/manifest"));
        second.createContext("/apiro/manifest", exchange -> reply(exchange, 401, new byte[0]));

        ManifestException error = assertThrows(ManifestException.class,
                () -> ManifestFetcher.fetch(address(first, "/apiro/manifest"), "secret"));

        assertTrue(error.getMessage().contains("another origin"));
    }

    @Test
    void stopsARedirectLoop() throws IOException {
        HttpServer server = server();
        redirect(server, "/loop", "/loop");

        ManifestException error = assertThrows(ManifestException.class,
                () -> ManifestFetcher.fetch(address(server, "/loop"), null));

        assertTrue(error.getMessage().contains("too many times"));
    }

    @Test
    void rejectsAnOversizedManifest() throws IOException {
        HttpServer server = server();
        byte[] large = new byte[20 * 1024 * 1024 + 1];
        Arrays.fill(large, (byte) ' ');
        server.createContext("/apiro/manifest", exchange -> reply(exchange, 200, large));

        ManifestException error = assertThrows(ManifestException.class,
                () -> ManifestFetcher.fetch(address(server, "/apiro/manifest"), null));

        assertTrue(error.getMessage().contains("20 MB"));
    }

    private HttpServer server() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        servers.add(server);
        return server;
    }

    private void serve(HttpServer server, String path, String body) {
        server.createContext(path, exchange -> {
            tokens.add(exchange.getRequestHeaders().getFirst(ManifestFetcher.TOKEN_HEADER));
            reply(exchange, 200, body.getBytes(StandardCharsets.UTF_8));
        });
    }

    private void redirect(HttpServer server, String path, String target) {
        server.createContext(path, exchange -> {
            tokens.add(exchange.getRequestHeaders().getFirst(ManifestFetcher.TOKEN_HEADER));
            exchange.getResponseHeaders().add("Location", target);
            reply(exchange, 302, new byte[0]);
        });
    }

    private static void reply(HttpExchange exchange, int status, byte[] body) throws IOException {
        exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        } finally {
            exchange.close();
        }
    }

    private static String address(HttpServer server, String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    private static String manifest(String application) {
        return "{\"manifestVersion\":1,\"generator\":{\"name\":\"test\"}," + application + "\"endpoints\":[]}";
    }
}
