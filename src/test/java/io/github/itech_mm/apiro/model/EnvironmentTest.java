package io.github.itech_mm.apiro.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class EnvironmentTest {

    @Test
    void generatesIdWhenMissing() {
        Environment env = Environment.of("Dev", List.of());
        assertFalse(env.id().isBlank());
    }

    @Test
    void findsEnabledVariableOnly() {
        Environment env = Environment.of("Dev", List.of(
                Variable.of("baseUrl", "http://localhost"),
                new Variable("old", "x", false, false)));
        assertEquals("http://localhost", env.find("baseUrl").orElseThrow().value());
        assertTrue(env.find("old").isEmpty());
    }

    @Test
    void secretVariableHasEmptyValue() {
        Variable token = Variable.secret("token");
        assertTrue(token.secret());
        assertEquals("", token.value());
    }

    @Test
    void variableListIsImmutable() {
        List<Variable> source = new ArrayList<>();
        source.add(Variable.of("a", "1"));
        Environment env = Environment.of("Dev", source);
        source.add(Variable.of("b", "2"));
        assertEquals(1, env.variables().size());
        assertThrows(UnsupportedOperationException.class, () -> env.variables().add(Variable.of("c", "3")));
    }
}