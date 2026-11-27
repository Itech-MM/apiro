package io.github.itech_mm.apiro.spring.webmvc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.admin.AdminController;
import com.shop.FileController;
import com.shop.HealthController;
import com.shop.OrderController;
import com.shop.ReportController;
import com.shop.UserController;
import com.shop.WebhookController;
import com.shop.WrapperController;
import io.github.itech_mm.apiro.spec.Generator;
import io.github.itech_mm.apiro.spec.Manifest;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.springframework.web.method.HandlerMethod;

class GoldenManifestTest {
    private static final String JSON = "application/json";

    private static MappingDescription mapping(Class<?> controller, String name, String pattern, String... methods)
            throws ReflectiveOperationException {
        java.lang.reflect.Method found = Arrays.stream(controller.getDeclaredMethods())
                .filter(method -> method.getName().equals(name)).findFirst()
                .orElseThrow(() -> new NoSuchMethodException(name));
        return new MappingDescription(new HandlerMethod(controller.getConstructor().newInstance(), found))
                .patterns(pattern).methods(methods);
    }

    @Test
    void writesTheOrdersExample() throws Exception {
        MappingDescription health = mapping(HealthController.class, "health", "/health", "GET");
        health.getProduces().add("text/plain");
        MappingDescription create = mapping(OrderController.class, "create", "/orders/{customerId}", "POST")
                .consumes(JSON);
        create.getProduces().add(JSON);
        MappingDescription get = mapping(OrderController.class, "get", "/orders/{id}", "GET");
        get.getProduces().add(JSON);
        Manifest manifest = new ManifestBuilder().applicationName("orders-service").contextPath("/shop")
                .authentication(Collections.singletonList("bearer"))
                .build(Arrays.asList(get, create, health)).getManifest();
        manifest.setGenerator(new Generator().setName("apiro-spring-webmvc").setVersion("1.0.0")
                .setFramework("spring-webmvc").setFrameworkVersion("6.2.0"));

        assertMatches("orders.json", manifest);
    }

    @Test
    void writesTheConditionsExample() throws Exception {
        MappingDescription first = mapping(ReportController.class, "listV1", "/reports", "GET");
        first.getParams().add("version=1");
        MappingDescription second = mapping(ReportController.class, "listV2", "/reports", "GET");
        second.getParams().add("version=2");
        MappingDescription json = mapping(ReportController.class, "importJson", "/reports", "POST").consumes(JSON);
        MappingDescription xml = mapping(ReportController.class, "importXml", "/reports", "POST")
                .consumes("application/xml");
        xml.getHeaders().add("X-Client=mobile");
        xml.getUnsupported().add("ApiVersionRequestCondition");
        MappingDescription file = mapping(FileController.class, "download", "/files/{id:\\d+}", "GET");

        assertMatches("conditions.json", build(xml, json, second, first, file));
    }

    @Test
    void writesTheMethodlessExample() throws Exception {
        assertMatches("methodless.json", build(mapping(WebhookController.class, "receive", "/webhook")));
    }

    @Test
    void writesTheGenericsExample() throws Exception {
        assertMatches("generics.json", build(
                mapping(AdminController.class, "create", "/admins", "POST"),
                mapping(UserController.class, "create", "/users", "POST"),
                mapping(WrapperController.class, "order", "/wrapped/order", "POST"),
                mapping(WrapperController.class, "user", "/wrapped/user", "POST")));
    }

    private static Manifest build(MappingDescription... mappings) {
        Manifest manifest = new ManifestBuilder().build(Arrays.asList(mappings)).getManifest();
        return manifest.setGenerator(new Generator().setName("apiro-spring-webmvc"));
    }

    private static void assertMatches(String name, Manifest manifest) throws IOException {
        String expected = compact(read(name)).replaceFirst("\"generatedAt\":\"[^\"]*\",", "");
        assertEquals(expected, ManifestJsonWriter.write(manifest.setGeneratedAt(null)));
    }

    private static String read(String name) throws IOException {
        try (InputStream in = GoldenManifestTest.class.getResourceAsStream("/examples/" + name)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int length;
            while ((length = in.read(buffer)) != -1) {
                out.write(buffer, 0, length);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static String compact(String json) {
        StringBuilder out = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (quoted && c == '\\') {
                out.append(c).append(json.charAt(++i));
                continue;
            }
            if (c == '"') {
                quoted = !quoted;
            }
            if (quoted || !Character.isWhitespace(c)) {
                out.append(c);
            }
        }
        return out.toString();
    }
}
