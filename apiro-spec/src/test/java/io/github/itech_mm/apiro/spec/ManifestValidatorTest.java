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

    private static Manifest withBodySchema(Schema schema) {
        return valid().setEndpoints(Arrays.asList(
                endpoint("x", "POST", "/a").setBody(new Body().setSchema(schema))));
    }

    @Test
    void sameRouteWithDifferentIdsIsAllowed() {
        Manifest manifest = valid().setEndpoints(Arrays.asList(
                endpoint("a", "GET", "/a"), endpoint("b", "GET", "/a")));
        assertEquals(0, ManifestValidator.validateCanonical(manifest).size());
    }

    @Test
    void sameRouteIsOrderedById() {
        Manifest manifest = valid().setEndpoints(Arrays.asList(
                endpoint("b", "GET", "/a"), endpoint("a", "GET", "/a")));
        assertTrue(mentions(ManifestValidator.validateCanonical(manifest), "out of order"));
    }

    @Test
    void anyMethodIsAccepted() {
        Manifest manifest = valid().setEndpoints(Arrays.asList(endpoint("a", "ANY", "/a")));
        assertEquals(0, ManifestValidator.validate(manifest).size());
    }

    @Test
    void unknownAuthenticationIsNotCanonical() {
        Manifest manifest = valid().setEndpoints(Arrays.asList(endpoint("a", "GET", "/a")
                .setSecurity(new Security().setAuthentication(new Authentication().setStatus("unknown")))));
        assertEquals(0, ManifestValidator.validate(manifest).size());
        assertTrue(mentions(ManifestValidator.validateCanonical(manifest), "authentication must be omitted"));
    }

    @Test
    void sameMethodOnDifferentPathsAndDifferentMethodOnSamePathAreFine() {
        Manifest manifest = valid().setEndpoints(Arrays.asList(
                endpoint("a", "GET", "/a"), endpoint("b", "POST", "/a"), endpoint("c", "GET", "/b")));
        assertEquals(0, ManifestValidator.validate(manifest).size());
    }

    @Test
    void itemsOnNonArrayIsReported() {
        Schema schema = new Schema().setType(SchemaType.STRING).setItems(new Schema().setType(SchemaType.STRING));
        assertTrue(mentions(ManifestValidator.validate(withBodySchema(schema)), ".items is only allowed on type 'array'"));
    }

    @Test
    void propertiesAndRequiredOnNonObjectAreReported() {
        Schema schema = new Schema().setType(SchemaType.ARRAY).setRequired(Arrays.asList("id"));
        schema.getProperties().put("id", new Schema().setType(SchemaType.STRING));
        List<String> problems = ManifestValidator.validate(withBodySchema(schema));
        assertTrue(mentions(problems, ".properties is only allowed on type 'object'"));
        assertTrue(mentions(problems, ".required is only allowed on type 'object'"));
    }

    @Test
    void requiredNameMustBeAProperty() {
        Schema schema = new Schema().setType(SchemaType.OBJECT).setRequired(Arrays.asList("missing"));
        schema.getProperties().put("id", new Schema().setType(SchemaType.STRING));
        assertTrue(mentions(ManifestValidator.validate(withBodySchema(schema)),
                "names 'missing' which is not in properties"));
    }

    @Test
    void contradictoryLimitsAreReported() {
        Schema schema = new Schema().setType(SchemaType.STRING).setMinLength(5).setMaxLength(2)
                .setMinimum(10.0).setMaximum(1.0).setMinItems(3).setMaxItems(1);
        List<String> problems = ManifestValidator.validate(withBodySchema(schema));
        assertTrue(mentions(problems, "minLength 5 is greater than maxLength 2"));
        assertTrue(mentions(problems, "minimum 10.0 is greater than maximum 1.0"));
        assertTrue(mentions(problems, "minItems 3 is greater than maxItems 1"));
    }

    @Test
    void negativeLimitsAreReported() {
        Schema schema = new Schema().setType(SchemaType.STRING).setMinLength(-1).setMaxItems(-2);
        List<String> problems = ManifestValidator.validate(withBodySchema(schema));
        assertTrue(mentions(problems, ".minLength must not be negative"));
        assertTrue(mentions(problems, ".maxItems must not be negative"));
    }

    @Test
    void refSchemaIgnoresShapeRules() {
        Manifest manifest = valid();
        manifest.getSchemas().put("Thing", new Schema().setType(SchemaType.OBJECT));
        Schema ref = new Schema().setRef("Thing").setType(SchemaType.STRING)
                .setItems(new Schema().setType(SchemaType.STRING));
        manifest.setEndpoints(Arrays.asList(endpoint("x", "POST", "/a").setBody(new Body().setSchema(ref))));
        assertEquals(0, ManifestValidator.validate(manifest).size());
    }

    @Test
    void validManifestIsCanonical() {
        assertEquals(0, ManifestValidator.validateCanonical(valid()).size());
    }

    @Test
    void canonicalIncludesStructuralProblems() {
        assertTrue(mentions(ManifestValidator.validateCanonical(valid().setManifestVersion(0)), "1 or higher"));
    }

    @Test
    void unsortedEndpointsAreOnlyACanonicalProblem() {
        Manifest manifest = valid().setEndpoints(Arrays.asList(
                endpoint("b", "GET", "/b"), endpoint("a", "GET", "/a")));
        assertEquals(0, ManifestValidator.validate(manifest).size());
        assertTrue(mentions(ManifestValidator.validateCanonical(manifest), "endpoints[1] is out of order"));
    }

    @Test
    void endpointsAreSortedByPathThenMethod() {
        Manifest sorted = valid().setEndpoints(Arrays.asList(
                endpoint("a", "DELETE", "/a"), endpoint("b", "GET", "/a"), endpoint("c", "POST", "/a"),
                endpoint("d", "GET", "/b")));
        assertEquals(0, ManifestValidator.validateCanonical(sorted).size());
        Manifest wrongMethodOrder = valid().setEndpoints(Arrays.asList(
                endpoint("a", "POST", "/a"), endpoint("b", "GET", "/a")));
        assertTrue(mentions(ManifestValidator.validateCanonical(wrongMethodOrder), "out of order"));
    }

    @Test
    void refWithOtherFieldsIsOnlyACanonicalProblem() {
        Manifest manifest = valid();
        manifest.getSchemas().put("Thing", new Schema().setType(SchemaType.OBJECT));
        Schema ref = new Schema().setRef("Thing").setNullable(Boolean.TRUE);
        manifest.setEndpoints(Arrays.asList(endpoint("x", "POST", "/a").setBody(new Body().setSchema(ref))));
        assertEquals(0, ManifestValidator.validate(manifest).size());
        assertTrue(mentions(ManifestValidator.validateCanonical(manifest), "has fields besides 'ref'"));
    }

    @Test
    void bareRefIsCanonical() {
        Manifest manifest = valid();
        manifest.getSchemas().put("Thing", new Schema().setType(SchemaType.OBJECT));
        manifest.setEndpoints(Arrays.asList(endpoint("x", "POST", "/a").setBody(
                new Body().setSchema(new Schema().setRef("Thing")))));
        assertEquals(0, ManifestValidator.validateCanonical(manifest).size());
    }

    @Test
    void schemaCapIsACanonicalProblem() {
        Manifest manifest = valid();
        for (int i = 0; i <= ManifestValidator.MAX_SCHEMAS; i++) {
            manifest.getSchemas().put("S" + i, new Schema().setType(SchemaType.OBJECT));
        }
        assertEquals(0, ManifestValidator.validate(manifest).size());
        assertTrue(mentions(ManifestValidator.validateCanonical(manifest), "the limit is 500"));
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
