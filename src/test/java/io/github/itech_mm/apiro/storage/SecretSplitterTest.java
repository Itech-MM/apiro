package io.github.itech_mm.apiro.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.itech_mm.apiro.model.Environment;
import io.github.itech_mm.apiro.model.Project;
import io.github.itech_mm.apiro.model.Variable;
import java.util.List;
import org.junit.jupiter.api.Test;

class SecretSplitterTest {

    private static Project projectWith(Variable... variables) {
        Environment env = new Environment("e1", "Dev", List.of(variables));
        return new Project(null, 0, "p1", "Shop", null, null, null, null, null, List.of(env), null);
    }

    @Test
    void splitMovesSecretValuesToLocalState() {
        Project project = projectWith(Variable.of("baseUrl", "http://localhost"),
                new Variable("token", "abc", true, true));
        SecretSplitter.Split split = SecretSplitter.split(project, LocalState.empty().withActiveEnvironment("e1"));
        Environment env = split.project().findEnvironment("e1").orElseThrow();
        assertEquals("", env.find("token").orElseThrow().value());
        assertTrue(env.find("token").orElseThrow().secret());
        assertEquals("http://localhost", env.find("baseUrl").orElseThrow().value());
        assertEquals("abc", split.state().secret("e1", "token"));
        assertEquals("e1", split.state().activeEnvironmentId());
    }

    @Test
    void splitDropsSecretsOfRemovedVariables() {
        LocalState state = LocalState.empty().withSecret("e1", "old", "x");
        SecretSplitter.Split split = SecretSplitter.split(projectWith(Variable.of("a", "1")), state);
        assertEquals("", split.state().secret("e1", "old"));
    }

    @Test
    void mergeRestoresSecretValues() {
        Project project = projectWith(Variable.secret("token"));
        LocalState state = LocalState.empty().withSecret("e1", "token", "abc");
        Project merged = SecretSplitter.merge(project, state);
        assertEquals("abc", merged.findEnvironment("e1").orElseThrow().find("token").orElseThrow().value());
    }

    @Test
    void mergeLeavesMissingSecretEmpty() {
        Project merged = SecretSplitter.merge(projectWith(Variable.secret("token")), LocalState.empty());
        assertEquals("", merged.findEnvironment("e1").orElseThrow().find("token").orElseThrow().value());
    }
}