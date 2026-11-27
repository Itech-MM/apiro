package io.github.itech_mm.apiro.service.manifest;

import io.github.itech_mm.apiro.model.BodyType;
import io.github.itech_mm.apiro.model.Folder;
import io.github.itech_mm.apiro.model.FormPart;
import io.github.itech_mm.apiro.model.KeyValue;
import io.github.itech_mm.apiro.model.Project;
import io.github.itech_mm.apiro.model.ProjectItem;
import io.github.itech_mm.apiro.model.RequestBody;
import io.github.itech_mm.apiro.model.RequestItem;
import io.github.itech_mm.apiro.model.Variable;
import io.github.itech_mm.apiro.service.manifest.ManifestDiff.Added;
import io.github.itech_mm.apiro.service.manifest.ManifestDiff.Match;
import io.github.itech_mm.apiro.service.manifest.ManifestDiff.Removed;
import io.github.itech_mm.apiro.service.manifest.ManifestDiff.Updated;
import io.github.itech_mm.apiro.service.project.ProjectTree;
import io.github.itech_mm.apiro.spec.Manifest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ManifestReimport {

    private static final Pattern REFERENCE = Pattern.compile("\\$\\{([^}]+)}");

    private record Fresh(Folder folder, RequestItem request) {
    }

    private ManifestReimport() {
    }

    public static ManifestDiff diff(Project project, Manifest manifest) {
        ManifestImporter.Result imported = ManifestImporter.importManifest(manifest);
        List<String> warnings = new ArrayList<>(imported.warnings());
        List<Fresh> fresh = new ArrayList<>();
        for (Folder folder : imported.folders()) {
            for (ProjectItem item : folder.items()) {
                fresh.add(new Fresh(folder, (RequestItem) item));
            }
        }
        List<RequestItem> existing = new ArrayList<>();
        collect(project.items(), existing);

        Map<Integer, Integer> pairs = new LinkedHashMap<>();
        Map<Integer, Match> kinds = new LinkedHashMap<>();
        pass(existing, fresh, id -> id, Match.ID, pairs, kinds);
        pass(existing, fresh, ManifestReimport::route, Match.ROUTE, pairs, kinds);
        pass(existing, fresh, ManifestReimport::handler, Match.HANDLER, pairs, kinds);

        List<Updated> updated = new ArrayList<>();
        int unchanged = 0;
        for (Map.Entry<Integer, Integer> pair : pairs.entrySet()) {
            RequestItem old = existing.get(pair.getKey());
            Fresh next = fresh.get(pair.getValue());
            List<String> changes = new ArrayList<>();
            RequestItem refreshed = refreshed(old, next.request(), changes, warnings);
            if (changes.isEmpty()) {
                unchanged++;
            } else {
                updated.add(new Updated(old.id(), old.name(), kinds.get(pair.getKey()), changes, refreshed,
                        placeholders(next)));
            }
        }
        List<Added> added = new ArrayList<>();
        for (int i = 0; i < fresh.size(); i++) {
            if (!pairs.containsValue(i)) {
                Fresh entry = fresh.get(i);
                added.add(new Added(entry.request().manifestEndpointId(), entry.folder().name(), entry.request(),
                        placeholders(entry), entry.folder().auth()));
            }
        }
        List<Removed> removed = new ArrayList<>();
        for (int i = 0; i < existing.size(); i++) {
            if (!pairs.containsKey(i)) {
                RequestItem old = existing.get(i);
                removed.add(new Removed(old.id(), old.name(), old.manifestEndpointId()));
            }
        }
        return new ManifestDiff(updated, added, removed, unchanged, warnings);
    }

    public static Project apply(Project project, ManifestDiff diff, Set<String> accepted) {
        Project result = project;
        for (Updated entry : diff.updated()) {
            if (accepted.contains(entry.key()) && result.findItem(entry.requestId()).isPresent()) {
                result = ProjectTree.withRequest(result, entry.request());
                result = withVariables(result, ProjectTree.parentOf(result, entry.requestId()).orElse(""),
                        entry.variables());
            }
        }
        for (Added entry : diff.added()) {
            if (!accepted.contains(entry.key())) {
                continue;
            }
            Optional<String> target = linkedFolder(result.items(), entry.folderName());
            if (target.isEmpty()) {
                Folder folder = new Folder(null, entry.folderName(), entry.folderAuth(), null, entry.variables(), null,
                        List.of(entry.request()));
                result = ProjectTree.withAdded(result, "", folder);
            } else {
                result = ProjectTree.withAdded(result, target.get(), entry.request());
                result = withVariables(result, target.get(), entry.variables());
            }
        }
        return result;
    }

    public static Project applyAll(Project project, ManifestDiff diff) {
        return apply(project, diff, diff.applicableKeys());
    }

    private static void collect(List<ProjectItem> items, List<RequestItem> into) {
        for (ProjectItem item : items) {
            if (item instanceof RequestItem request && request.manifestEndpointId() != null) {
                into.add(request);
            } else if (item instanceof Folder folder) {
                collect(folder.items(), into);
            }
        }
    }

    private static void pass(List<RequestItem> existing, List<Fresh> fresh, Function<String, String> key, Match match,
            Map<Integer, Integer> pairs, Map<Integer, Match> kinds) {
        Map<String, List<Integer>> olds = new LinkedHashMap<>();
        for (int i = 0; i < existing.size(); i++) {
            String k = pairs.containsKey(i) ? null : key.apply(existing.get(i).manifestEndpointId());
            if (k != null) {
                olds.computeIfAbsent(k, x -> new ArrayList<>()).add(i);
            }
        }
        Map<String, List<Integer>> news = new LinkedHashMap<>();
        Set<Integer> taken = new HashSet<>(pairs.values());
        for (int i = 0; i < fresh.size(); i++) {
            String id = fresh.get(i).request().manifestEndpointId();
            String k = taken.contains(i) || id == null ? null : key.apply(id);
            if (k != null) {
                news.computeIfAbsent(k, x -> new ArrayList<>()).add(i);
            }
        }
        for (Map.Entry<String, List<Integer>> entry : olds.entrySet()) {
            List<Integer> other = news.get(entry.getKey());
            if (other != null && other.size() == 1 && entry.getValue().size() == 1) {
                pairs.put(entry.getValue().get(0), other.get(0));
                kinds.put(entry.getValue().get(0), match);
            }
        }
    }

    private static String route(String id) {
        int hash = id.indexOf('#');
        return hash < 0 ? id : id.substring(0, hash);
    }

    private static String handler(String id) {
        int hash = id.indexOf('#');
        int space = id.indexOf(' ');
        if (hash < 0 || space < 0 || space > hash) {
            return null;
        }
        return id.substring(0, space) + id.substring(hash);
    }

    private static RequestItem refreshed(RequestItem old, RequestItem next, List<String> changes,
            List<String> warnings) {
        if (old.method() != next.method()) {
            changes.add("Method " + old.method() + " to " + next.method());
        }
        if (!old.url().equals(next.url())) {
            changes.add("URL " + old.url() + " to " + next.url());
        }
        List<KeyValue> query = withMissing(old.query(), next.query(), "query parameter", changes);
        List<KeyValue> headers = withMissing(old.headers(), next.headers(), "header", changes);
        RequestBody body = body(old, next.body(), changes, warnings);
        if (!old.manifestEndpointId().equals(next.manifestEndpointId())) {
            changes.add("Linked endpoint is now " + next.manifestEndpointId());
        }
        return new RequestItem(old.id(), old.name(), next.method(), next.url(), query, headers, old.auth(), body,
                old.settings(), next.manifestEndpointId());
    }

    private static List<KeyValue> withMissing(List<KeyValue> current, List<KeyValue> wanted, String what,
            List<String> changes) {
        List<KeyValue> result = new ArrayList<>(current);
        Set<String> keys = new HashSet<>();
        current.forEach(pair -> keys.add(pair.key()));
        for (KeyValue pair : wanted) {
            if (keys.add(pair.key())) {
                result.add(pair);
                changes.add("Added " + what + " " + pair.key());
            }
        }
        return result;
    }

    private static RequestBody body(RequestItem old, RequestBody wanted, List<String> changes, List<String> warnings) {
        RequestBody current = old.body();
        if (current.type() == BodyType.NONE && wanted.type() != BodyType.NONE) {
            changes.add("Added " + wanted.type() + " body");
            return wanted;
        }
        if (current.type() != wanted.type()) {
            warnings.add(old.name() + ": the endpoint body is now " + wanted.type() + ", your " + current.type()
                    + " body was kept");
            return current;
        }
        if (current.type() == BodyType.JSON && current.text().isBlank() && !wanted.text().isBlank()) {
            changes.add("Filled the empty JSON body");
            return new RequestBody(current.type(), wanted.text(), current.contentType(), current.charset(),
                    current.form(), current.parts(), current.filePath());
        }
        if (current.type() == BodyType.FORM) {
            List<KeyValue> form = withMissing(current.form(), wanted.form(), "form field", changes);
            return form.size() == current.form().size() ? current
                    : new RequestBody(current.type(), current.text(), current.contentType(), current.charset(), form,
                            current.parts(), current.filePath());
        }
        if (current.type() == BodyType.MULTIPART) {
            List<FormPart> parts = new ArrayList<>(current.parts());
            Set<String> names = new HashSet<>();
            current.parts().forEach(part -> names.add(part.name()));
            for (FormPart part : wanted.parts()) {
                if (names.add(part.name())) {
                    parts.add(part);
                    changes.add("Added multipart part " + part.name());
                }
            }
            return parts.size() == current.parts().size() ? current
                    : new RequestBody(current.type(), current.text(), current.contentType(), current.charset(),
                            current.form(), parts, current.filePath());
        }
        return current;
    }

    private static List<Variable> placeholders(Fresh entry) {
        Set<String> used = new HashSet<>();
        Matcher matcher = REFERENCE.matcher(entry.request().url());
        while (matcher.find()) {
            used.add(matcher.group(1));
        }
        List<Variable> result = new ArrayList<>();
        for (Variable variable : entry.folder().variables()) {
            if (used.contains(variable.key())) {
                result.add(variable);
            }
        }
        return result;
    }

    private static Optional<String> linkedFolder(List<ProjectItem> items, String name) {
        for (ProjectItem item : items) {
            if (item instanceof Folder folder) {
                boolean linked = folder.items().stream()
                        .anyMatch(child -> child instanceof RequestItem r && r.manifestEndpointId() != null);
                if (linked && folder.name().equals(name)) {
                    return Optional.of(folder.id());
                }
                Optional<String> nested = linkedFolder(folder.items(), name);
                if (nested.isPresent()) {
                    return nested;
                }
            }
        }
        return Optional.empty();
    }

    private static Project withVariables(Project project, String folderId, List<Variable> wanted) {
        if (folderId.isEmpty() || wanted.isEmpty()) {
            return project;
        }
        Optional<ProjectItem> found = project.findItem(folderId);
        if (found.isEmpty() || !(found.get() instanceof Folder folder)) {
            return project;
        }
        Set<String> keys = new HashSet<>();
        folder.variables().forEach(variable -> keys.add(variable.key()));
        List<Variable> merged = new ArrayList<>(folder.variables());
        for (Variable variable : wanted) {
            if (keys.add(variable.key())) {
                merged.add(variable);
            }
        }
        if (merged.size() == folder.variables().size()) {
            return project;
        }
        return ProjectTree.withFolder(project, new Folder(folder.id(), folder.name(), folder.auth(), folder.headers(),
                merged, folder.settings(), folder.items()));
    }
}
