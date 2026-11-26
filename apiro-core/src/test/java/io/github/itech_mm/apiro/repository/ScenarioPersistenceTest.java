package io.github.itech_mm.apiro.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import io.github.itech_mm.apiro.model.Assertion;
import io.github.itech_mm.apiro.model.BodyOverride;
import io.github.itech_mm.apiro.model.Edge;
import io.github.itech_mm.apiro.model.Extractor;
import io.github.itech_mm.apiro.model.FieldOverride;
import io.github.itech_mm.apiro.model.Folder;
import io.github.itech_mm.apiro.model.HttpMethod;
import io.github.itech_mm.apiro.model.KeyValue;
import io.github.itech_mm.apiro.model.OnFailure;
import io.github.itech_mm.apiro.model.Project;
import io.github.itech_mm.apiro.model.RequestItem;
import io.github.itech_mm.apiro.model.ScenarioItem;
import io.github.itech_mm.apiro.model.Step;
import io.github.itech_mm.apiro.model.Variable;
import io.github.itech_mm.apiro.service.project.ProjectTree;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ScenarioPersistenceTest {

    private static ScenarioItem checkout() {
        Step login = new Step("s1", "r1", "Login",
                List.of(new Extractor("token", Extractor.Source.JSON_PATH, "$.data.token"),
                        new Extractor("status", Extractor.Source.STATUS, "")),
                List.of(new Assertion(Assertion.Source.STATUS, "", Assertion.Operator.EQUALS, "200")),
                250, OnFailure.CONTINUE, 40.5, 80);
        Step order = new Step("s2", "r2", "Create order", null,
                List.of(new Assertion(Assertion.Source.JSON_PATH, "$.state", Assertion.Operator.EQUALS,
                        "CONFIRMED")),
                0, null, 320, 80);
        return new ScenarioItem("sc1", "Checkout", List.of(Variable.of("email", "a@b.c")),
                List.of(login, order), List.of(new Edge("s1", "s2")), OnFailure.STOP);
    }

    private static ScenarioItem flowWithHeaders() {
        Step home = new Step("s1", "r1", "Home", null, null,
                List.of(KeyValue.of("Authorization", "Bearer ${token}"), new KeyValue("X-Off", "1", false)),
                0, null, 0, 0);
        return new ScenarioItem("sc1", "Flow", null, List.of(home), null, OnFailure.STOP);
    }

    private static Project projectWith(ScenarioItem scenario) {
        RequestItem request = RequestItem.of("Login", HttpMethod.POST, "{{baseUrl}}/login");
        Folder folder = Folder.of("Flows", List.of(scenario));
        return new Project(null, 0, "p1", "Shop", null, null, null, null, null, null, List.of(request, folder));
    }

    private static int writtenVersion(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        return root.get("version").getAsInt();
    }

    @Test
    void roundTripKeepsEveryScenarioField() {
        ScenarioItem original = checkout();
        Project loaded = ProjectJson.fromJson(ProjectJson.toJson(projectWith(original)));
        assertEquals(original, loaded.findItem("sc1").orElseThrow());
    }

    @Test
    void scenarioIsWrittenWithItsOwnTypeTag() {
        String json = ProjectJson.toJson(projectWith(checkout()));
        JsonObject folder = JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("items").get(1)
                .getAsJsonObject();
        JsonObject scenario = folder.getAsJsonArray("items").get(0).getAsJsonObject();
        assertEquals("scenario", scenario.get("type").getAsString());
        assertEquals(2, scenario.getAsJsonArray("steps").size());
        assertEquals(1, scenario.getAsJsonArray("edges").size());
    }

    @Test
    void roundTripThroughStoreKeepsScenario(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("shop.apiro.json");
        ProjectStore.save(projectWith(checkout()), LocalState.empty(), file);
        ProjectStore.Opened opened = ProjectStore.open(file);
        assertEquals(checkout(), opened.project().findItem("sc1").orElseThrow());
    }

    @Test
    void minimalScenarioGetsDefaults() {
        String json = "{\"format\":\"apiro-project\",\"version\":2,\"items\":[{\"type\":\"scenario\",\"id\":\"sc\"}]}";
        ScenarioItem scenario = assertInstanceOf(ScenarioItem.class,
                ProjectJson.fromJson(json).findItem("sc").orElseThrow());
        assertEquals("", scenario.name());
        assertTrue(scenario.steps().isEmpty());
        assertTrue(scenario.edges().isEmpty());
        assertTrue(scenario.variables().isEmpty());
        assertEquals(OnFailure.STOP, scenario.onFailure());
    }

    @Test
    void minimalStepGetsDefaults() {
        String json = "{\"format\":\"apiro-project\",\"version\":2,\"items\":[{\"type\":\"scenario\",\"id\":\"sc\","
                + "\"steps\":[{\"requestId\":\"r1\"}]}]}";
        ScenarioItem scenario = (ScenarioItem) ProjectJson.fromJson(json).findItem("sc").orElseThrow();
        Step step = scenario.steps().get(0);
        assertFalse(step.id().isBlank());
        assertEquals("r1", step.requestId());
        assertEquals("", step.name());
        assertTrue(step.extractors().isEmpty());
        assertTrue(step.assertions().isEmpty());
        assertEquals(0, step.delayMillis());
        assertNull(step.onFailure());
    }

    @Test
    void negativeDelayIsClampedToZero() {
        Step step = new Step("s", "r", "n", null, null, -5, null, 0, 0);
        assertEquals(0, step.delayMillis());
    }

    @Test
    void fileWithScenarioIsWrittenAsVersionTwo() {
        int version = writtenVersion(ProjectJson.toJson(projectWith(checkout())));
        assertEquals(Project.SCENARIO_VERSION, version);
        assertTrue(version > Project.BASE_VERSION);
    }

    @Test
    void stepHeadersRoundTripWithTheirEnabledFlag() {
        ScenarioItem original = flowWithHeaders();
        Project loaded = ProjectJson.fromJson(ProjectJson.toJson(projectWith(original)));
        ScenarioItem scenario = (ScenarioItem) loaded.findItem("sc1").orElseThrow();
        assertEquals(original, scenario);
        assertEquals(2, scenario.steps().get(0).headers().size());
        assertFalse(scenario.steps().get(0).headers().get(1).enabled());
    }

    @Test
    void fileWithStepHeadersIsWrittenAsVersionThree() {
        int version = writtenVersion(ProjectJson.toJson(projectWith(flowWithHeaders())));
        assertEquals(Project.HEADERS_VERSION, version);
    }

    @Test
    void fileWithBodyOverrideIsWrittenAsVersionFourAndRoundTrips() {
        Step step = new Step("s1", "r1", "Login", null, null, null, 0, null, 0, 0,
                BodyOverride.patch(List.of(FieldOverride.of("$.age", FieldOverride.ValueType.NUMBER, "42"),
                        FieldOverride.of("$.promo", FieldOverride.ValueType.REMOVE, ""))));
        ScenarioItem scenario = new ScenarioItem("sc1", "Flow", null, List.of(step), null, null);
        String json = ProjectJson.toJson(projectWith(scenario));

        assertEquals(Project.OVERRIDES_VERSION, writtenVersion(json));
        ScenarioItem loaded = (ScenarioItem) ProjectJson.fromJson(json).findItem("sc1").orElseThrow();
        assertEquals(step, loaded.steps().get(0));
    }

    @Test
    void versionTwoFileWithoutStepHeadersLoadsAndStaysVersionTwo() {
        String json = "{\"format\":\"apiro-project\",\"version\":2,\"items\":[{\"type\":\"scenario\",\"id\":\"sc\","
                + "\"steps\":[{\"id\":\"s1\",\"requestId\":\"r1\"}]}]}";
        Project loaded = ProjectJson.fromJson(json);
        ScenarioItem scenario = (ScenarioItem) loaded.findItem("sc").orElseThrow();
        assertTrue(scenario.steps().get(0).headers().isEmpty());
        assertEquals(Project.SCENARIO_VERSION, writtenVersion(ProjectJson.toJson(loaded)));
    }

    @Test
    void stepWithoutHeadersGetsAnEmptyList() {
        Step step = new Step("s", "r", "n", null, null, null, 0, null, 0, 0);
        assertTrue(step.headers().isEmpty());
        assertEquals(step, new Step("s", "r", "n", null, null, 0, null, 0, 0));
    }

    @Test
    void fileWithoutScenarioStaysVersionOne() {
        Project plain = new Project(null, 0, "p1", "Shop", null, null, null, null, null, null,
                List.of(RequestItem.of("Login", HttpMethod.GET, "/")));
        assertEquals(Project.BASE_VERSION, writtenVersion(ProjectJson.toJson(plain)));
    }

    @Test
    void removingTheLastScenarioGoesBackToVersionOne() {
        Project loaded = ProjectJson.fromJson(ProjectJson.toJson(projectWith(checkout())));
        assertEquals(Project.SCENARIO_VERSION, loaded.version());
        Project without = ProjectTree.without(loaded, "sc1");
        assertEquals(Project.BASE_VERSION, writtenVersion(ProjectJson.toJson(without)));
    }

    @Test
    void versionOneFileStillLoads() {
        String json = "{\"format\":\"apiro-project\",\"version\":1,\"id\":\"p1\",\"name\":\"Old\",\"items\":["
                + "{\"type\":\"folder\",\"id\":\"f1\",\"name\":\"Users\",\"items\":["
                + "{\"type\":\"request\",\"id\":\"r1\",\"name\":\"List\",\"method\":\"GET\",\"url\":\"/users\"}]}]}";
        Project loaded = ProjectJson.fromJson(json);
        assertEquals(1, loaded.version());
        assertInstanceOf(RequestItem.class, loaded.findItem("r1").orElseThrow());
        assertEquals(Project.BASE_VERSION, writtenVersion(ProjectJson.toJson(loaded)));
    }

    @Test
    void newerThanCurrentVersionIsRefused() {
        String json = "{\"format\":\"apiro-project\",\"version\":" + (Project.CURRENT_VERSION + 1) + "}";
        assertThrows(JsonParseException.class, () -> ProjectJson.fromJson(json));
    }

    @Test
    void unknownItemTypeIsStillRefused() {
        String json = "{\"format\":\"apiro-project\",\"version\":2,\"items\":[{\"type\":\"chart\",\"id\":\"x\"}]}";
        assertThrows(JsonParseException.class, () -> ProjectJson.fromJson(json));
    }
}
