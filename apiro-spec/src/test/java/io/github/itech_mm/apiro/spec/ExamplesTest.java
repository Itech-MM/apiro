package io.github.itech_mm.apiro.spec;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * The example manifests are the contract other modules test against. Spec itself has no JSON
 * library, so this only guards packaging; parsing and writing are checked in the consumer modules.
 */
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

    @Test
    void minimalExampleIsPackaged() throws IOException {
        assertTrue(read("minimal.json").contains("\"manifestVersion\": " + ManifestVersion.CURRENT));
    }

    @Test
    void ordersExampleIsPackaged() throws IOException {
        assertTrue(read("orders.json").contains("\"manifestVersion\": " + ManifestVersion.CURRENT));
    }
}
