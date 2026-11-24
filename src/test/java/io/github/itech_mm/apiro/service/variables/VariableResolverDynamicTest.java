package io.github.itech_mm.apiro.service.variables;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.itech_mm.apiro.model.HttpMethod;
import io.github.itech_mm.apiro.model.Project;
import io.github.itech_mm.apiro.model.RequestItem;
import io.github.itech_mm.apiro.model.Variable;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class VariableResolverDynamicTest {

    private static VariableResolver resolver(Variable... variables) {
        return VariableResolver.of(List.of(List.of(variables)));
    }

    @Test
    void dynamicReferenceIsReplaced() {
        String value = resolver().resolve("id=${$uuid}");
        assertTrue(value.matches("id=[0-9a-f-]{36}"), value);
    }

    @Test
    void eachOccurrenceGetsItsOwnValue() {
        String[] parts = resolver().resolve("${$uuid}|${$uuid}").split("\\|");
        assertEquals(2, parts.length);
        assertNotEquals(parts[0], parts[1]);
    }

    @Test
    void unknownDynamicNameStaysAndIsUndefined() {
        VariableResolver resolver = resolver();
        assertEquals("${$nope}", resolver.resolve("${$nope}"));
        List<VariableResolver.Reference> references = resolver.scan("${$nope} ${$uuid}");
        assertEquals(2, references.size());
        assertFalse(references.get(0).defined());
        assertTrue(references.get(1).defined());
    }

    @Test
    void declaredVariableWinsOverBuiltIn() {
        assertEquals("fixed", resolver(Variable.of("$uuid", "fixed")).resolve("${$uuid}"));
    }

    @Test
    void injectedClockMakesTimestampsStable() {
        DynamicVariables dynamic = new DynamicVariables(new Random(1),
                Clock.fixed(Instant.parse("2026-11-24T10:15:30Z"), ZoneOffset.UTC));
        VariableResolver resolver = VariableResolver.of(List.of(List.of()), dynamic);
        assertEquals(String.valueOf(Instant.parse("2026-11-24T10:15:30Z").toEpochMilli()),
                resolver.resolve("${$timestamp}"));
    }

    @Test
    void dynamicNamesAreNotListedAsUserVariables() {
        assertFalse(resolver(Variable.of("a", "1")).names().contains("$uuid"));
    }

    @Test
    void runtimeLayerBeatsEveryOtherLayer() {
        RequestItem request = new RequestItem("r1", "r1", HttpMethod.GET, "", null, null, null, null, null);
        Project project = new Project(null, 0, null, "Shop", null, null, null, null,
                List.of(Variable.of("token", "project")), List.of(), List.of(request));
        assertEquals("project", VariableResolver.forItem(project, "", "r1").resolve("${token}"));
        assertEquals("runtime", VariableResolver
                .forItem(project, "", "r1", List.of(Variable.of("token", "runtime"))).resolve("${token}"));
    }
}
