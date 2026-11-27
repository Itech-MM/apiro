package io.github.itech_mm.apiro.spec;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ExamplesTest {
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

    private static void assertFollowsFormatRules(String name) throws IOException {
        String json = read(name);
        assertTrue(json.contains("\"manifestVersion\": " + ManifestVersion.CURRENT), name + ": manifestVersion");
        // rule 8: endpoints is always written
        assertTrue(json.contains("\"endpoints\""), name + ": endpoints must always be written");
        // rule 9: key names that are legal identifiers in every language
        assertFalse(json.contains("\"$ref\""), name + ": use 'ref', not '$ref'");
        assertFalse(json.contains("\"enum\""), name + ": use 'enumValues', not 'enum'");
        // section 3: the manifest never asserts a base URL
        assertFalse(json.contains("\"baseUrl\""), name + ": a manifest never carries baseUrl");
    }

    @Test
    void minimalExampleIsPackagedAndFollowsTheFormatRules() throws IOException {
        assertFollowsFormatRules("minimal.json");
    }

    @Test
    void ordersExampleIsPackagedAndFollowsTheFormatRules() throws IOException {
        assertFollowsFormatRules("orders.json");
    }
}
