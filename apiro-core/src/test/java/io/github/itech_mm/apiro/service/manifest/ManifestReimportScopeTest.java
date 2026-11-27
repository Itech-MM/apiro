package io.github.itech_mm.apiro.service.manifest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.itech_mm.apiro.model.Folder;
import io.github.itech_mm.apiro.model.HttpMethod;
import io.github.itech_mm.apiro.model.Project;
import io.github.itech_mm.apiro.model.ProjectItem;
import io.github.itech_mm.apiro.model.RequestItem;
import io.github.itech_mm.apiro.service.manifest.ManifestReimport.LinkedFolder;
import io.github.itech_mm.apiro.service.project.ProjectTree;
import io.github.itech_mm.apiro.spec.Endpoint;
import io.github.itech_mm.apiro.spec.Generator;
import io.github.itech_mm.apiro.spec.Handler;
import io.github.itech_mm.apiro.spec.Manifest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ManifestReimportScopeTest {

    private static Endpoint endpoint(String method, String path, String group, String className, String name) {
        return new Endpoint()
                .setId(method + " " + path + "#" + className + "." + name + "()")
                .setMethod(method)
                .setPath(path)
                .setGroup(group)
                .setName(name)
                .setHandler(new Handler().setClassName(className).setMethodName(name));
    }

    private static Manifest manifest(Endpoint... endpoints) {
        return new Manifest().setManifestVersion(1).setGenerator(new Generator().setName("test"))
                .setEndpoints(Arrays.asList(endpoints));
    }

    private static Manifest usersApp() {
        return manifest(
                endpoint("GET", "/users", "UserController", "a.UserController", "list"),
                endpoint("POST", "/users", "UserController", "a.UserController", "create"));
    }

    private static Manifest billingApp() {
        return manifest(endpoint("GET", "/invoices", "BillingController", "b.BillingController", "list"));
    }

    private static Project withImports(Manifest... manifests) {
        Project project = Project.create("p");
        for (Manifest manifest : manifests) {
            project = ManifestImporter.addTo(project, ManifestImporter.importManifest(manifest));
        }
        return project;
    }

    private static List<Folder> folders(Project project) {
        List<Folder> result = new ArrayList<>();
        for (ProjectItem item : project.items()) {
            if (item instanceof Folder folder) {
                result.add(folder);
            }
        }
        return result;
    }

    private static Folder folderNamed(Project project, String name) {
        return folders(project).stream().filter(folder -> folder.name().equals(name)).findFirst().orElseThrow();
    }

    private static List<String> requestNames(Folder folder) {
        List<String> names = new ArrayList<>();
        for (ProjectItem item : folder.items()) {
            names.add(item.name());
        }
        return names;
    }

    @Test
    void wholeProjectDiffReportsOtherApplicationsAsRemoved() {
        Project project = withImports(usersApp(), billingApp());
        ManifestDiff diff = ManifestReimport.diff(project, usersApp());
        assertNull(diff.scope());
        assertEquals(1, diff.removed().size());
        assertEquals("GET /invoices#b.BillingController.list()", diff.removed().get(0).endpointId());
        assertEquals(2, diff.unchanged());
    }

    @Test
    void scopedDiffLeavesOtherFoldersAlone() {
        Project project = withImports(usersApp(), billingApp());
        Set<String> scope = Set.of(folderNamed(project, "UserController").id());
        ManifestDiff diff = ManifestReimport.diff(project, usersApp(), scope);
        assertEquals(scope, diff.scope());
        assertTrue(diff.removed().isEmpty());
        assertTrue(diff.added().isEmpty());
        assertTrue(diff.updated().isEmpty());
        assertEquals(2, diff.unchanged());
    }

    @Test
    void scopedDiffStillReportsRemovedRequestsInsideTheScope() {
        Project project = withImports(usersApp(), billingApp());
        Set<String> scope = Set.of(folderNamed(project, "UserController").id());
        Manifest fewer = manifest(endpoint("GET", "/users", "UserController", "a.UserController", "list"));
        ManifestDiff diff = ManifestReimport.diff(project, fewer, scope);
        assertEquals(1, diff.removed().size());
        assertEquals("create", diff.removed().get(0).requestName());
        assertEquals(1, diff.unchanged());
    }

    @Test
    void emptyScopeTreatsEverythingAsNewAndKeepsTheRestUntouched() {
        Project project = withImports(billingApp());
        ManifestDiff diff = ManifestReimport.diff(project, usersApp(), Set.of());
        assertEquals(2, diff.added().size());
        assertTrue(diff.removed().isEmpty());
        Project result = ManifestReimport.applyAll(project, diff);
        assertEquals(2, folders(result).size());
        assertEquals(List.of("list"), requestNames(folderNamed(result, "BillingController")));
        assertEquals(2, folderNamed(result, "UserController").items().size());
    }

    @Test
    void newRequestsGoToTheScopedFolderEvenWhenAnotherApplicationUsesTheSameFolderName() {
        Manifest other = manifest(endpoint("GET", "/b", "ApiController", "b.ApiController", "list"));
        Manifest mine = manifest(endpoint("GET", "/a", "ApiController", "a.ApiController", "list"));
        Project project = withImports(other, mine);
        Folder otherFolder = folders(project).get(0);
        Folder myFolder = folders(project).get(1);
        assertEquals(otherFolder.name(), myFolder.name());

        Manifest grown = manifest(
                endpoint("GET", "/a", "ApiController", "a.ApiController", "list"),
                endpoint("POST", "/a", "ApiController", "a.ApiController", "create"));
        ManifestDiff diff = ManifestReimport.diff(project, grown, Set.of(myFolder.id()));
        assertEquals(1, diff.added().size());
        Project result = ManifestReimport.applyAll(project, diff);

        assertEquals(2, folders(result).size());
        assertEquals(List.of("list"), requestNames((Folder) result.findItem(otherFolder.id()).orElseThrow()));
        assertEquals(List.of("list", "create"), requestNames((Folder) result.findItem(myFolder.id()).orElseThrow()));
    }

    @Test
    void emptyScopeDoesNotReuseAnotherApplicationsFolderOfTheSameName() {
        Manifest other = manifest(endpoint("GET", "/b", "ApiController", "b.ApiController", "list"));
        Project project = withImports(other);
        Manifest mine = manifest(
                endpoint("GET", "/a", "ApiController", "a.ApiController", "list"),
                endpoint("POST", "/a", "ApiController", "a.ApiController", "create"));
        ManifestDiff diff = ManifestReimport.diff(project, mine, Set.of());
        Project result = ManifestReimport.applyAll(project, diff);
        List<Folder> all = folders(result);
        assertEquals(2, all.size());
        assertEquals(1, all.get(0).items().size());
        assertEquals(2, all.get(1).items().size());
    }

    private static RequestItem linked(String name, String endpointId) {
        RequestItem plain = RequestItem.of(name, HttpMethod.GET, "${baseUrl}/x");
        return new RequestItem(plain.id(), plain.name(), plain.method(), plain.url(), plain.query(), plain.headers(),
                plain.auth(), plain.body(), plain.settings(), endpointId);
    }

    @Test
    void linkedFoldersListsOnlyFoldersThatDirectlyHoldLinkedRequests() {
        Project project = withImports(usersApp(), billingApp());
        Folder notes = Folder.of("Notes", List.of(RequestItem.of("scratch", HttpMethod.GET, "http://x")));
        Folder inner = Folder.of("Inner", List.of(linked("deep", "GET /deep#d.Deep.get()")));
        Folder outer = Folder.of("Outer", List.of(inner));
        project = ProjectTree.withAdded(project, "", notes);
        project = ProjectTree.withAdded(project, "", outer);
        project = ProjectTree.withAdded(project, "", linked("loose", "GET /loose#l.Loose.get()"));

        List<LinkedFolder> found = ManifestReimport.linkedFolders(project);
        List<String> paths = new ArrayList<>();
        for (LinkedFolder folder : found) {
            paths.add(folder.path() + ":" + folder.requests());
        }
        assertEquals(List.of("(project root):1", "UserController:2", "BillingController:1", "Outer / Inner:1"), paths);
        assertEquals("", found.get(0).id());
    }

    @Test
    void rootRequestsAreOnlyComparedWhenTheRootIsInScope() {
        RequestItem loose = linked("list", "GET /users#a.UserController.list()");
        Project project = ProjectTree.withAdded(Project.create("p"), "", loose);
        Manifest manifest = manifest(endpoint("GET", "/users", "UserController", "a.UserController", "list"));
        ManifestDiff with = ManifestReimport.diff(project, manifest, Set.of(""));
        assertEquals(1, with.updated().size(), "matched, so compared (its URL differs from the fresh one)");
        assertTrue(with.added().isEmpty());
        ManifestDiff without = ManifestReimport.diff(project, manifest, Set.of("some-folder"));
        assertEquals(1, without.added().size());
        assertTrue(without.removed().isEmpty());
        assertEquals(0, without.unchanged());
    }

    @Test
    void scopeIsCopiedAndKeepsNullForWholeProject() {
        Set<String> source = new HashSet<>(Set.of("x"));
        ManifestDiff diff = new ManifestDiff(List.of(), List.of(), List.of(), 0, List.of(), source);
        source.add("y");
        assertEquals(Set.of("x"), diff.scope());
        assertNull(new ManifestDiff(List.of(), List.of(), List.of(), 0, List.of(), null).scope());
    }
}
