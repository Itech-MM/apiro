package io.github.itech_mm.apiro.service.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.itech_mm.apiro.model.AuthConfig;
import io.github.itech_mm.apiro.model.AuthType;
import io.github.itech_mm.apiro.model.Folder;
import io.github.itech_mm.apiro.model.HttpMethod;
import io.github.itech_mm.apiro.model.KeyValue;
import io.github.itech_mm.apiro.model.Project;
import io.github.itech_mm.apiro.model.ProjectItem;
import io.github.itech_mm.apiro.model.RequestItem;
import io.github.itech_mm.apiro.model.RequestSettings;
import java.util.List;
import org.junit.jupiter.api.Test;

class InheritanceTest {

    private static Project project(AuthConfig auth, List<KeyValue> headers, RequestSettings settings,
                                   List<ProjectItem> items) {
        return new Project(null, 0, null, "Shop", null, settings, auth, headers, null, null, items);
    }

    private static Folder folder(String id, AuthConfig auth, List<KeyValue> headers, RequestSettings settings,
                                 List<ProjectItem> items) {
        return new Folder(id, id, auth, headers, null, settings, items);
    }

    private static RequestItem request(String id, AuthConfig auth, List<KeyValue> headers) {
        return new RequestItem(id, id, HttpMethod.GET, "", null, headers, auth, null, null);
    }

    @Test
    void requestUsesProjectAuthWhenNull() {
        Project project = project(AuthConfig.bearer("p"), null, null, List.of(request("r", null, null)));
        Inheritance.Effective effective = Inheritance.forItem(project, "r").orElseThrow();
        assertEquals(AuthType.BEARER, effective.auth().type());
        assertEquals("p", effective.auth().token());
    }

    @Test
    void inheritTypeBehavesLikeNull() {
        Project project = project(AuthConfig.bearer("p"), null, null,
                List.of(request("r", AuthConfig.inherit(), null)));
        assertEquals("p", Inheritance.forItem(project, "r").orElseThrow().auth().token());
    }

    @Test
    void nearestAuthWins() {
        RequestItem inheriting = request("a", AuthConfig.inherit(), null);
        RequestItem overriding = request("b", AuthConfig.apiKey("X-Key", "k"), null);
        Folder folder = folder("f", AuthConfig.basic("u", "p"), null, null, List.of(inheriting, overriding));
        Project project = project(AuthConfig.bearer("p"), null, null, List.of(folder));
        assertEquals(AuthType.BASIC, Inheritance.forItem(project, "a").orElseThrow().auth().type());
        assertEquals(AuthType.API_KEY, Inheritance.forItem(project, "b").orElseThrow().auth().type());
    }

    @Test
    void headersMergeByKeyIgnoringCase() {
        RequestItem request = request("r", null, List.of(KeyValue.of("X-Id", "1")));
        Folder folder = folder("f", null, List.of(KeyValue.of("accept", "text/plain")), null, List.of(request));
        Project project = project(null, List.of(KeyValue.of("Accept", "application/json")), null, List.of(folder));
        List<KeyValue> headers = Inheritance.forItem(project, "r").orElseThrow().headers();
        assertEquals(2, headers.size());
        assertEquals("text/plain", headers.get(0).value());
        assertEquals("X-Id", headers.get(1).key());
    }

    @Test
    void disabledHeaderRemovesInheritedOne() {
        RequestItem request = request("r", null, List.of(new KeyValue("Accept", "", false)));
        Project project = project(null, List.of(KeyValue.of("Accept", "application/json")), null,
                List.of(request));
        assertTrue(Inheritance.forItem(project, "r").orElseThrow().headers().isEmpty());
    }

    @Test
    void settingsMergeFieldByFieldWithDefaults() {
        RequestSettings projectSettings = new RequestSettings(5000, null, null, null, null, null);
        RequestSettings folderSettings = new RequestSettings(null, 7000, false, null, null, null);
        Folder folder = folder("f", null, null, folderSettings, List.of(request("r", null, null)));
        Project project = project(null, null, projectSettings, List.of(folder));
        RequestSettings effective = Inheritance.forItem(project, "r").orElseThrow().settings();
        assertEquals(5000, effective.connectTimeoutMillis());
        assertEquals(7000, effective.socketTimeoutMillis());
        assertEquals(Boolean.FALSE, effective.followRedirects());
        assertEquals(5, effective.maxRedirects());
        assertEquals("", effective.proxyHost());
    }

    @Test
    void folderIdResolvesToFolderValues() {
        Folder folder = folder("f", AuthConfig.basic("u", "p"), null, null, List.of());
        Project project = project(null, null, null, List.of(folder));
        assertEquals(AuthType.BASIC, Inheritance.forItem(project, "f").orElseThrow().auth().type());
    }

    @Test
    void unknownIdReturnsEmpty() {
        assertTrue(Inheritance.forItem(project(null, null, null, List.of()), "missing").isEmpty());
    }

    @Test
    void projectAuthNeverInherits() {
        Project project = project(AuthConfig.inherit(), null, null, List.of(request("r", null, null)));
        assertEquals(AuthType.NONE, Inheritance.forItem(project, "r").orElseThrow().auth().type());
    }
}