package io.github.itech_mm.apiro.spec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class ManifestValidatorTest {
    private static Manifest valid() {
        return new Manifest()
                .setManifestVersion(ManifestVersion.CURRENT)
                .setGenerator(new Generator().setName("test"))
                .setEndpoints(Arrays.asList(
                        new Endpoint().setId("GET /ping#P.ping()").setMethod("GET").setPath("/ping")));
    }

    private static Endpoint endpoint(String id, String method, String path) {
        return new Endpoint().setId(id).setMethod(method).setPath(path);
    }

    private static boolean mentions(List<String> problems, String text) {
        for (String problem : problems) {
            if (problem.contains(text)) {
                return true;
            }
        }
        return false;
    }

    @Test
    void validManifestHasNoProblems() {
        assertEquals(0, ManifestValidator.validate(valid()).size());
    }

    @Test
    void nullManifestIsReportedNotThrown() {
        assertTrue(mentions(ManifestValidator.validate(null), "null"));
    }

    @Test
    void newerManifestVersionIsRejected() {
        Manifest manifest = valid().setManifestVersion(ManifestVersion.CURRENT + 1);
        assertTrue(mentions(ManifestValidator.validate(manifest), "newer than the supported"));
    }

    @Test
    void versionZeroIsRejected() {
        assertTrue(mentions(ManifestValidator.validate(valid().setManifestVersion(0)), "1 or higher"));
    }

    @Test
    void generatorNameIsRequired() {
        Manifest manifest = valid().setGenerator(new Generator());
        assertTrue(mentions(ManifestValidator.validate(manifest), "generator.name"));
    }

    @Test
    void duplicateIdsAreReported() {
        Manifest manifest = valid().setEndpoints(Arrays.asList(
                endpoint("same", "GET", "/a"), endpoint("same", "POST", "/a")));
        assertTrue(mentions(ManifestValidator.validate(manifest), "duplicated"));
    }

    @Test
    void unknownMethodAndRelativePathAreReported() {
        Manifest manifest = valid().setEndpoints(Arrays.asList(endpoint("x", "FETCH", "relative")));
        List<String> problems = ManifestValidator.validate(manifest);
        assertTrue(mentions(problems, ".method"));
        assertTrue(mentions(problems, ".path"));
    }

    @Test
    void pathVariableNeedsPathParameter() {
        Manifest manifest = valid().setEndpoints(Arrays.asList(endpoint("x", "GET", "/orders/{id}")));
        assertTrue(mentions(ManifestValidator.validate(manifest), "{id} has no 'path' parameter"));
    }

    @Test
    void pathParameterMustAppearInPath() {
        Endpoint endpoint = endpoint("x", "GET", "/orders").setParameters(Arrays.asList(
                new Parameter().setName("id").setIn(ParameterLocation.PATH).setRequired(true)));
        Manifest manifest = valid().setEndpoints(Arrays.asList(endpoint));
        assertTrue(mentions(ManifestValidator.validate(manifest), "is not in the path"));
    }

    @Test
    void pathParameterMustBeRequired() {
        Endpoint endpoint = endpoint("x", "GET", "/orders/{id}").setParameters(Arrays.asList(
                new Parameter().setName("id").setIn(ParameterLocation.PATH)));
        Manifest manifest = valid().setEndpoints(Arrays.asList(endpoint));
        assertTrue(mentions(ManifestValidator.validate(manifest), "must be required"));
    }

    @Test
    void unknownParameterLocationIsReported() {
        Endpoint endpoint = endpoint("x", "GET", "/a").setParameters(Arrays.asList(
                new Parameter().setName("q").setIn("body")));
        Manifest manifest = valid().setEndpoints(Arrays.asList(endpoint));
        assertTrue(mentions(ManifestValidator.validate(manifest), ".in must be one of"));
    }

    @Test
    void unknownSchemaRefIsReported() {
        Endpoint endpoint = endpoint("x", "POST", "/a").setBody(
                new Body().setSchema(new Schema().setRef("Missing")));
        Manifest manifest = valid().setEndpoints(Arrays.asList(endpoint));
        assertTrue(mentions(ManifestValidator.validate(manifest), "unknown schema 'Missing'"));
    }

    @Test
    void knownSchemaRefAndRecursiveSchemaAreAccepted() {
        Schema node = new Schema().setType(SchemaType.OBJECT);
        node.getProperties().put("children", new Schema().setType(SchemaType.ARRAY)
                .setItems(new Schema().setRef("Node")));
        Manifest manifest = valid();
        manifest.getSchemas().put("Node", node);
        manifest.setEndpoints(Arrays.asList(endpoint("x", "POST", "/a").setBody(
                new Body().setSchema(new Schema().setRef("Node")))));
        assertEquals(0, ManifestValidator.validate(manifest).size());
    }

    @Test
    void unknownSchemaTypeIsReported() {
        Manifest manifest = valid();
        manifest.getSchemas().put("Bad", new Schema().setType("decimal"));
        assertTrue(mentions(ManifestValidator.validate(manifest), "schemas.Bad.type"));
    }

    @Test
    void settersCopyCollectionsAndNeverStoreNull() {
        Endpoint endpoint = new Endpoint().setConsumes(null);
        assertEquals(0, endpoint.getConsumes().size());
        List<String> source = Arrays.asList("application/json");
        endpoint.setProduces(source);
        assertEquals(source, endpoint.getProduces());
    }

    @Test
    void equalsFollowsContent() {
        assertEquals(valid(), valid());
        assertEquals(valid().hashCode(), valid().hashCode());
    }
}
