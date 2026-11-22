package io.github.itech_mm.apiro.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParseException;
import io.github.itech_mm.apiro.model.Environment;
import io.github.itech_mm.apiro.model.Folder;
import io.github.itech_mm.apiro.model.HttpMethod;
import io.github.itech_mm.apiro.model.Project;
import io.github.itech_mm.apiro.model.ProjectItem;
import io.github.itech_mm.apiro.model.RequestItem;
import io.github.itech_mm.apiro.model.Variable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectStoreTest {

    private static Project sample() {
        RequestItem request = new RequestItem("r1", "List users", HttpMethod.GET, "{{baseUrl}}/users", null, null,
                null, null, null);
        Folder folder = new Folder("f1", "Users", null, null, null, null, List.of(request));
        Environment dev = new Environment("e1", "Dev", List.of(
                Variable.of("baseUrl", "http://localhost"),
                new Variable("token", "abc", true, true)));
        return new Project(null, 0, "p1", "Shop", null, null, null, null, null, List.of(dev), List.of(folder));
    }

    @Test
    void jsonRoundTripKeepsItemTree() {
        Project loaded = ProjectJson.fromJson(ProjectJson.toJson(sample()));
        ProjectItem found = loaded.findItem("r1").orElseThrow();
        assertInstanceOf(RequestItem.class, found);
        assertEquals("{{baseUrl}}/users", ((RequestItem) found).url());
        assertInstanceOf(Folder.class, loaded.findItem("f1").orElseThrow());
    }

    @Test
    void rejectsForeignFormat() {
        assertThrows(JsonParseException.class, () -> ProjectJson.fromJson("{\"format\":\"other\",\"version\":1}"));
    }

    @Test
    void rejectsNewerVersion() {
        String json = "{\"format\":\"apiro-project\",\"version\":99}";
        assertThrows(JsonParseException.class, () -> ProjectJson.fromJson(json));
    }

    @Test
    void rejectsItemWithoutType() {
        String json = "{\"format\":\"apiro-project\",\"version\":1,\"items\":[{\"id\":\"x\"}]}";
        assertThrows(JsonParseException.class, () -> ProjectJson.fromJson(json));
    }

    @Test
    void saveKeepsSecretsOutOfProjectFile(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("shop.apiro.json");
        ProjectStore.save(sample(), LocalState.empty().withActiveEnvironment("e1"), file);
        String text = Files.readString(file, StandardCharsets.UTF_8);
        assertFalse(text.contains("abc"));
        assertTrue(text.contains("http://localhost"));
        assertTrue(Files.exists(LocalStateStore.pathFor(file, "p1")));
    }

    @Test
    void openRestoresSecretsAndActiveEnvironment(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("shop.apiro.json");
        ProjectStore.save(sample(), LocalState.empty().withActiveEnvironment("e1"), file);
        ProjectStore.Opened opened = ProjectStore.open(file);
        Variable token = opened.project().findEnvironment("e1").orElseThrow().find("token").orElseThrow();
        assertEquals("abc", token.value());
        assertTrue(token.secret());
        assertEquals("e1", opened.state().activeEnvironmentId());
    }

    @Test
    void openWithoutLocalFileGivesEmptySecrets(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("shop.apiro.json");
        ProjectStore.save(sample(), LocalState.empty(), file);
        Files.delete(LocalStateStore.pathFor(file, "p1"));
        ProjectStore.Opened opened = ProjectStore.open(file);
        assertEquals("", opened.project().findEnvironment("e1").orElseThrow().find("token").orElseThrow().value());
    }
}