package io.github.itech_mm.apiro.spring.webmvc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.itech_mm.apiro.spec.Endpoint;
import io.github.itech_mm.apiro.spec.Generator;
import io.github.itech_mm.apiro.spec.Manifest;
import io.github.itech_mm.apiro.spec.Schema;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class ManifestJsonWriterTest {
    private static Manifest manifest() {
        return new Manifest().setManifestVersion(1).setGenerator(new Generator().setName("t"));
    }

    @Test
    void writesCompactJsonAndOmitsDefaults() {
        Manifest manifest = manifest().setEndpoints(Arrays.asList(
                new Endpoint().setId("a").setMethod("GET").setPath("/a")));

        assertEquals("{\"manifestVersion\":1,\"generator\":{\"name\":\"t\"},\"endpoints\":"
                + "[{\"id\":\"a\",\"method\":\"GET\",\"path\":\"/a\"}]}", ManifestJsonWriter.write(manifest));
    }

    @Test
    void escapesTextAndWritesWholeNumbersWithoutAPoint() {
        Manifest manifest = manifest();
        manifest.getSchemas().put("B", new Schema().setType("number").setMinimum(100d).setMaximum(100.5));
        manifest.getSchemas().put("A", new Schema().setType("string").setPattern("\"\\\n"));

        assertEquals("{\"manifestVersion\":1,\"generator\":{\"name\":\"t\"},\"endpoints\":[],\"schemas\":{"
                + "\"A\":{\"type\":\"string\",\"pattern\":\"\\\"\\\\\\u000a\"},"
                + "\"B\":{\"type\":\"number\",\"minimum\":100,\"maximum\":100.5}}}",
                ManifestJsonWriter.write(manifest));
    }
}
