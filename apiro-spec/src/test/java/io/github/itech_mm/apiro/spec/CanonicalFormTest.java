package io.github.itech_mm.apiro.spec;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CanonicalFormTest {
    private static String manifest(String endpointFields) {
        return "{\"manifestVersion\":1,\"generator\":{\"name\":\"t\"},\"endpoints\":[{\"id\":\"GET /a#c.m()\","
                + "\"method\":\"GET\",\"path\":\"/a\"" + endpointFields + "}]}";
    }

    private static String withSchema(String schema) {
        return "{\"manifestVersion\":1,\"generator\":{\"name\":\"t\"},\"endpoints\":[],\"schemas\":{\"A\":"
                + schema + "}}";
    }

    @Test
    void plainManifestHasNoDrift() {
        assertNull(CanonicalTree.drift(manifest("")));
    }

    @Test
    void generatedAtIsIgnored() {
        assertNull(CanonicalTree.drift(
                "{\"manifestVersion\":1,\"generator\":{\"name\":\"t\"},\"generatedAt\":\"2026-11-26T10:15:30Z\","
                        + "\"endpoints\":[]}"));
    }

    @Test
    void writtenFalseIsDrift() {
        assertNotNull(CanonicalTree.drift(manifest(",\"deprecated\":false")));
    }

    @Test
    void emptyArrayIsDrift() {
        assertNotNull(CanonicalTree.drift(manifest(",\"consumes\":[]")));
    }

    @Test
    void emptyObjectIsDrift() {
        assertNotNull(CanonicalTree.drift(manifest(",\"security\":{}")));
    }

    @Test
    void emptyEndpointsArrayIsRequired() {
        assertNotNull(CanonicalTree.drift("{\"manifestVersion\":1,\"generator\":{\"name\":\"t\"}}"));
    }

    @Test
    void nestedSecurityIsWritten() {
        assertNull(CanonicalTree.drift(manifest(
                ",\"security\":{\"authorization\":{\"status\":\"declared\",\"access\":\"open\"}}")));
    }

    @Test
    void fieldOrderIsDrift() {
        assertNotNull(CanonicalTree.drift("{\"generator\":{\"name\":\"t\"},\"manifestVersion\":1,\"endpoints\":[]}"));
    }

    @Test
    void decimalPointOnWholeNumberIsDrift() {
        assertNotNull(CanonicalTree.drift(withSchema("{\"type\":\"integer\",\"minimum\":1.0}")));
    }

    @Test
    void fractionIsKept() {
        assertNull(CanonicalTree.drift(withSchema("{\"type\":\"number\",\"minimum\":0.5}")));
    }

    @Test
    void unknownFieldIsRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> ManifestJsonReader.read(manifest(",\"consume\":[\"application/json\"]")));
        assertTrue(error.getMessage().contains("consume"));
    }

    @Test
    void decimalPointOnWholeNumberFieldIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> ManifestJsonReader.read(withSchema("{\"type\":\"string\",\"minLength\":1.0}")));
    }

    @Test
    void nullIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> ManifestJsonReader.read(manifest(",\"group\":null")));
    }

    @Test
    void duplicateKeyIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> ManifestJsonReader.read("{\"manifestVersion\":1,\"manifestVersion\":1}"));
    }

    @Test
    void trailingContentIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> ManifestJsonReader.read(manifest("") + " x"));
    }
}
