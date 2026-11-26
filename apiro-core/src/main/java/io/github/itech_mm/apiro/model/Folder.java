package io.github.itech_mm.apiro.model;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public record Folder(String id, String name, AuthConfig auth, List<KeyValue> headers, List<Variable> variables,
                     RequestSettings settings, List<ProjectItem> items) implements ProjectItem {

    public Folder {
        id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
        name = name == null ? "" : name;
        headers = headers == null ? List.of() : List.copyOf(headers);
        variables = variables == null ? List.of() : List.copyOf(variables);
        items = items == null ? List.of() : List.copyOf(items);
    }

    public static Folder of(String name, List<ProjectItem> items) {
        return new Folder(null, name, null, null, null, null, items);
    }

    public Optional<ProjectItem> find(String targetId) {
        for (ProjectItem item : items) {
            if (item.id().equals(targetId)) {
                return Optional.of(item);
            }
            if (item instanceof Folder folder) {
                Optional<ProjectItem> found = folder.find(targetId);
                if (found.isPresent()) {
                    return found;
                }
            }
        }
        return Optional.empty();
    }
}