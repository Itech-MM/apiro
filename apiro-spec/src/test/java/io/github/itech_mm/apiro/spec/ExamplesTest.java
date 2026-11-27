package io.github.itech_mm.apiro.spec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;

class ExamplesTest {
    private static final List<String> EXAMPLES = Arrays.asList(
            "minimal.json", "orders.json", "conditions.json", "methodless.json", "generics.json", "security.json");

    static Stream<String> examples() {
        return EXAMPLES.stream();
    }

    private static String read(String name) throws IOException {
        try (InputStream in = ExamplesTest.class.getResourceAsStream("/examples/" + name)) {
            assertNotNull(in, "missing resource /examples/" + name);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void everyExampleOnDiskIsListed() throws Exception {
        Path directory = Paths.get(ExamplesTest.class.getResource("/examples").toURI());
        List<String> found = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.json")) {
            for (Path path : stream) {
                found.add(path.getFileName().toString());
            }
        }
        Collections.sort(found);
        List<String> listed = new ArrayList<>(EXAMPLES);
        Collections.sort(listed);
        assertEquals(listed, found);
    }

    @ParameterizedTest
    @MethodSource("examples")
    void exampleDeclaresTheCurrentVersion(String name) throws IOException {
        assertEquals(ManifestVersion.CURRENT, ManifestJsonReader.read(read(name)).getManifestVersion());
    }

    @ParameterizedTest
    @MethodSource("examples")
    void exampleIsValidAndCanonical(String name) throws IOException {
        Manifest manifest = ManifestJsonReader.read(read(name));
        assertEquals(Collections.emptyList(), ManifestValidator.validateCanonical(manifest));
    }

    @ParameterizedTest
    @MethodSource("examples")
    void exampleIsWrittenInCanonicalForm(String name) throws IOException {
        assertNull(CanonicalTree.drift(read(name)));
    }

    @ParameterizedTest
    @MethodSource("examples")
    void exampleIdsFollowTheHandler(String name) throws IOException {
        Manifest manifest = ManifestJsonReader.read(read(name));
        for (Endpoint endpoint : manifest.getEndpoints()) {
            Handler handler = endpoint.getHandler();
            if (handler == null) {
                continue;
            }
            String pattern = endpoint.getPattern() == null ? endpoint.getPath() : endpoint.getPattern();
            String expected = endpoint.getMethod() + " " + pattern + "#" + handler.getClassName() + "."
                    + handler.getMethodName() + "(" + String.join(",", handler.getParameterTypes()) + ")";
            assertEquals(expected, endpoint.getId());
        }
    }

    @Test
    void ordersExampleCarriesTheDocumentedShape() throws IOException {
        Manifest manifest = ManifestJsonReader.read(read("orders.json"));
        assertEquals("/shop", manifest.getApplication().getContextPath());
        assertEquals(3, manifest.getEndpoints().size());
        assertEquals(new LinkedHashSet<>(Arrays.asList("com.shop.CreateOrder", "com.shop.OrderLine")), manifest.getSchemas().keySet());

        Endpoint health = manifest.getEndpoints().get(0);
        assertNull(health.getSecurity());

        Endpoint create = manifest.getEndpoints().get(1);
        assertEquals("POST", create.getMethod());
        assertEquals(Collections.singletonList("bearer"), create.getSecurity().getAuthentication().getSchemes());
        assertEquals("com.shop.CreateOrder", create.getBody().getSchema().getRef());
        assertEquals(4, create.getHandler().getParameterTypes().size());

        Endpoint get = manifest.getEndpoints().get(2);
        assertTrue(get.isDeprecated());
        assertEquals("configuration", get.getSecurity().getAuthentication().getSource());
        assertEquals("restricted", get.getSecurity().getAuthorization().getAccess());
    }
}
