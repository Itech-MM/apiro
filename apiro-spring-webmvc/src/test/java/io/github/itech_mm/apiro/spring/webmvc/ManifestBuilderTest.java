package io.github.itech_mm.apiro.spring.webmvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.itech_mm.apiro.spec.Endpoint;
import io.github.itech_mm.apiro.spec.ManifestValidator;
import io.github.itech_mm.apiro.spec.Parameter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.method.HandlerMethod;

class ManifestBuilderTest {
    static class OrderController {
        public void create(@PathVariable("id") long id,
                           @RequestParam(value = "dryRun", defaultValue = "false") boolean dryRun,
                           @RequestHeader(value = "X-Trace", required = false) String trace,
                           @RequestBody Object order) {
        }

        public void any() {
        }
    }

    private static MappingDescription mapping(String method, Class<?>... types) throws NoSuchMethodException {
        return new MappingDescription(new HandlerMethod(new OrderController(),
                OrderController.class.getMethod(method, types)));
    }

    @Test
    void readsRoutesParametersAndIds() throws NoSuchMethodException {
        ManifestBuilder.Result result = new ManifestBuilder().build(Arrays.asList(
                mapping("create", long.class, boolean.class, String.class, Object.class)
                        .patterns("/orders/{id:\\d{1,9}}").methods("POST").consumes("application/json"),
                mapping("any").patterns("/any")));

        assertEquals(Collections.emptyList(), ManifestValidator.validateCanonical(result.getManifest()));
        assertEquals(Collections.emptyList(), result.getWarnings());
        List<Endpoint> endpoints = result.getManifest().getEndpoints();
        assertEquals("ANY", endpoints.get(0).getMethod());
        Endpoint create = endpoints.get(1);
        assertEquals("/orders/{id}", create.getPath());
        assertEquals("POST /orders/{id:\\d{1,9}}#" + OrderController.class.getName()
                + ".create(long,boolean,java.lang.String,java.lang.Object)", create.getId());
        Parameter id = create.getParameters().get(0);
        assertEquals("int64", id.getSchema().getFormat());
        assertEquals("\\d{1,9}", id.getSchema().getPattern());
        assertEquals("false", create.getParameters().get(1).getDefaultValue());
        assertEquals("header", create.getParameters().get(2).getIn());
        assertTrue(create.getBody().isRequired());
        assertEquals("any", create.getBody().getSchema().getType());
    }

    @Test
    void leavesOutExcludedPackages() throws NoSuchMethodException {
        ManifestBuilder.Result result = new ManifestBuilder()
                .excludePackages(Collections.singletonList("io.github.itech_mm"))
                .build(Collections.singletonList(mapping("any").patterns("/any")));

        assertTrue(result.getManifest().getEndpoints().isEmpty());
    }
}
