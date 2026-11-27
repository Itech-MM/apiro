package io.github.itech_mm.apiro.spec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ModelTest {
    private static void assertDiffers(Object base, Object changed) {
        assertNotEquals(base, changed);
    }

    private static Schema schema() {
        Map<String, Schema> properties = new LinkedHashMap<>();
        properties.put("a", new Schema().setType(SchemaType.STRING));
        return new Schema().setType("object").setFormat("f").setRef("r")
                .setItems(new Schema().setType(SchemaType.STRING)).setProperties(properties)
                .setRequired(Arrays.asList("a")).setEnumValues(Arrays.asList("x")).setNullable(Boolean.TRUE)
                .setMinimum(1.0).setMaximum(2.0).setMinLength(1).setMaxLength(2).setPattern("p")
                .setMinItems(1).setMaxItems(2).setExample("e");
    }

    private static Parameter parameter() {
        return new Parameter().setName("n").setIn(ParameterLocation.QUERY).setRequired(true)
                .setDefaultValue("d").setDescription("desc").setSchema(schema());
    }

    private static Body body() {
        return new Body().setContentType("application/json").setRequired(true).setSchema(schema());
    }

    private static Handler handler() {
        return new Handler().setClassName("C").setMethodName("m").setParameterTypes(Arrays.asList("int"));
    }

    private static Security security() {
        return new Security()
                .setAuthentication(new Authentication().setStatus("declared").setSchemes(Arrays.asList("bearer")))
                .setAuthorization(new Authorization().setStatus("declared").setAccess("restricted"));
    }

    private static Generator generator() {
        return new Generator().setName("g").setVersion("1").setFramework("f").setFrameworkVersion("2");
    }

    private static Application application() {
        return new Application().setName("a").setContextPath("/c").setServletPath("/s");
    }

    private static Endpoint endpoint() {
        return new Endpoint().setId("id").setMethod("GET").setPath("/p").setGroup("g").setName("n")
                .setSummary("s").setDeprecated(true).setConsumes(Arrays.asList("a/b"))
                .setProduces(Arrays.asList("c/d")).setParameters(Arrays.asList(parameter()))
                .setBody(body()).setSecurity(security()).setHandler(handler());
    }

    private static Manifest manifest() {
        Manifest manifest = new Manifest().setManifestVersion(1).setGenerator(generator())
                .setGeneratedAt("2026-01-01T00:00:00Z").setApplication(application())
                .setEndpoints(Arrays.asList(endpoint()));
        manifest.getSchemas().put("S", schema());
        return manifest;
    }

    private static void assertEqualAndSameHash(Object a, Object b) {
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void parameterLocationsMatchTheDesign() {
        assertEquals(Arrays.asList("path", "query", "header", "cookie", "form", "part"),
                new ArrayList<>(ParameterLocation.ALL));
    }

    @Test
    void schemaTypesMatchTheDesign() {
        assertEquals(Arrays.asList("string", "integer", "number", "boolean", "array", "object", "file", "any"),
                new ArrayList<>(SchemaType.ALL));
    }

    @Test
    void constantSetsCannotBeModified() {
        assertThrows(UnsupportedOperationException.class, () -> ParameterLocation.ALL.add("x"));
        assertThrows(UnsupportedOperationException.class, () -> SchemaType.ALL.add("x"));
    }

    @Test
    void securityKeepsAuthenticationApartFromAuthorization() {
        assertNull(new Security().getAuthentication());
        assertNull(new Security().getAuthorization());
    }

    @Test
    void newObjectsStartWithDefaults() {
        Schema schema = new Schema();
        assertTrue(schema.getProperties().isEmpty());
        assertTrue(schema.getRequired().isEmpty());
        assertTrue(schema.getEnumValues().isEmpty());
        assertNull(schema.getNullable());
        assertNull(schema.getMinimum());
        Endpoint endpoint = new Endpoint();
        assertTrue(endpoint.getParameters().isEmpty());
        assertTrue(endpoint.getConsumes().isEmpty());
        assertTrue(endpoint.getProduces().isEmpty());
        assertNull(endpoint.getBody());
        assertNull(endpoint.getSecurity());
        Manifest manifest = new Manifest();
        assertTrue(manifest.getEndpoints().isEmpty());
        assertTrue(manifest.getSchemas().isEmpty());
        assertTrue(new Handler().getParameterTypes().isEmpty());
        assertTrue(new Authentication().getSchemes().isEmpty());
    }

    @Test
    void schemaEqualsCoversEveryField() {
        assertEqualAndSameHash(schema(), schema());
        assertDiffers(schema(), schema().setType("array"));
        assertDiffers(schema(), schema().setFormat("g"));
        assertDiffers(schema(), schema().setRef("q"));
        assertDiffers(schema(), schema().setItems(new Schema().setType(SchemaType.NUMBER)));
        assertDiffers(schema(), schema().setProperties(null));
        assertDiffers(schema(), schema().setRequired(null));
        assertDiffers(schema(), schema().setEnumValues(null));
        assertDiffers(schema(), schema().setNullable(Boolean.FALSE));
        assertDiffers(schema(), schema().setMinimum(0.0));
        assertDiffers(schema(), schema().setMaximum(3.0));
        assertDiffers(schema(), schema().setMinLength(0));
        assertDiffers(schema(), schema().setMaxLength(3));
        assertDiffers(schema(), schema().setPattern("q"));
        assertDiffers(schema(), schema().setMinItems(0));
        assertDiffers(schema(), schema().setMaxItems(3));
        assertDiffers(schema(), schema().setExample("q"));
    }

    @Test
    void parameterEqualsCoversEveryField() {
        assertEqualAndSameHash(parameter(), parameter());
        assertDiffers(parameter(), parameter().setName("m"));
        assertDiffers(parameter(), parameter().setIn(ParameterLocation.HEADER));
        assertDiffers(parameter(), parameter().setRequired(false));
        assertDiffers(parameter(), parameter().setDefaultValue("e"));
        assertDiffers(parameter(), parameter().setDescription("other"));
        assertDiffers(parameter(), parameter().setSchema(new Schema()));
    }

    @Test
    void bodyEqualsCoversEveryField() {
        assertEqualAndSameHash(body(), body());
        assertDiffers(body(), body().setContentType("text/plain"));
        assertDiffers(body(), body().setRequired(false));
        assertDiffers(body(), body().setSchema(new Schema()));
    }

    @Test
    void handlerEqualsCoversEveryField() {
        assertEqualAndSameHash(handler(), handler());
        assertDiffers(handler(), handler().setClassName("D"));
        assertDiffers(handler(), handler().setMethodName("n"));
        assertDiffers(handler(), handler().setParameterTypes(Arrays.asList("long")));
    }

    @Test
    void securityEqualsCoversEveryField() {
        assertEqualAndSameHash(security(), security());
        assertDiffers(security(), security().setAuthentication(new Authentication()));
        assertDiffers(security(), security().setAuthorization(new Authorization()));
    }

    @Test
    void generatorEqualsCoversEveryField() {
        assertEqualAndSameHash(generator(), generator());
        assertDiffers(generator(), generator().setName("h"));
        assertDiffers(generator(), generator().setVersion("2"));
        assertDiffers(generator(), generator().setFramework("g"));
        assertDiffers(generator(), generator().setFrameworkVersion("3"));
    }

    @Test
    void applicationEqualsCoversEveryField() {
        assertEqualAndSameHash(application(), application());
        assertDiffers(application(), application().setName("b"));
        assertDiffers(application(), application().setContextPath("/d"));
        assertDiffers(application(), application().setServletPath("/t"));
    }

    @Test
    void endpointEqualsCoversEveryField() {
        assertEqualAndSameHash(endpoint(), endpoint());
        assertDiffers(endpoint(), endpoint().setId("other"));
        assertDiffers(endpoint(), endpoint().setMethod("POST"));
        assertDiffers(endpoint(), endpoint().setPath("/q"));
        assertDiffers(endpoint(), endpoint().setGroup("h"));
        assertDiffers(endpoint(), endpoint().setName("o"));
        assertDiffers(endpoint(), endpoint().setSummary("t"));
        assertDiffers(endpoint(), endpoint().setPattern("/q"));
        assertDiffers(endpoint(), endpoint().setConditions(new Conditions()));
        assertDiffers(endpoint(), endpoint().setDeprecated(false));
        assertDiffers(endpoint(), endpoint().setConsumes(null));
        assertDiffers(endpoint(), endpoint().setProduces(null));
        assertDiffers(endpoint(), endpoint().setParameters(null));
        assertDiffers(endpoint(), endpoint().setBody(null));
        assertDiffers(endpoint(), endpoint().setSecurity(null));
        assertDiffers(endpoint(), endpoint().setHandler(null));
    }

    @Test
    void manifestEqualsCoversEveryField() {
        assertEqualAndSameHash(manifest(), manifest());
        assertDiffers(manifest(), manifest().setManifestVersion(2));
        assertDiffers(manifest(), manifest().setGenerator(null));
        assertDiffers(manifest(), manifest().setGeneratedAt("2027-01-01T00:00:00Z"));
        assertDiffers(manifest(), manifest().setApplication(null));
        assertDiffers(manifest(), manifest().setEndpoints(null));
        assertDiffers(manifest(), manifest().setSchemas(null));
    }

    @Test
    void settersCopyCollectionsSoLaterChangesDoNotLeakIn() {
        List<String> source = new ArrayList<>(Arrays.asList("a"));
        Handler handler = new Handler().setParameterTypes(source);
        source.add("b");
        assertEquals(Arrays.asList("a"), handler.getParameterTypes());
        Map<String, Schema> schemas = new LinkedHashMap<>();
        Manifest manifest = new Manifest().setSchemas(schemas);
        schemas.put("S", new Schema());
        assertTrue(manifest.getSchemas().isEmpty());
    }
}
