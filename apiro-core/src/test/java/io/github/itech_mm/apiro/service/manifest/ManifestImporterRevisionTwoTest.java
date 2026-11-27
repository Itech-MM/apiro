package io.github.itech_mm.apiro.service.manifest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.itech_mm.apiro.model.AuthType;
import io.github.itech_mm.apiro.model.HttpMethod;
import io.github.itech_mm.apiro.model.RequestItem;
import org.junit.jupiter.api.Test;

class ManifestImporterRevisionTwoTest {

    private static final String ANY = "{\"manifestVersion\":1,\"generator\":{\"name\":\"t\"},\"endpoints\":["
            + "{\"id\":\"a\",\"method\":\"ANY\",\"path\":\"/hook\",\"security\":{\"authentication\":"
            + "{\"status\":\"declared\",\"schemes\":[\"bearer\"]}}}]}";

    @Test
    void importsAnyAsGetWithAWarning() {
        ManifestImporter.Result result = ManifestImporter.importManifest(ManifestReader.read(ANY));

        RequestItem request = (RequestItem) result.folders().get(0).items().get(0);
        assertEquals(HttpMethod.GET, request.method());
        assertTrue(result.anyMethodIds().contains("a"));
        assertTrue(result.warnings().get(0).contains("accepts any method"));
    }

    @Test
    void suggestsTheDeclaredAuthentication() {
        ManifestImporter.Result result = ManifestImporter.importManifest(ManifestReader.read(ANY));

        assertEquals(AuthType.BEARER, result.folders().get(0).auth().type());
    }
}
