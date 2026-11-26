package io.github.itech_mm.apiro.model;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public record Project(String format, int version, String id, String name, String description,
                      RequestSettings settings, AuthConfig auth, List<KeyValue> headers,
                      List<Variable> variables, List<Environment> environments,
                      List<ProjectItem> items) {

    public static final String FORMAT = "apiro-project";
    public static final int BASE_VERSION = 1;
    public static final int SCENARIO_VERSION = 2;
    public static final int HEADERS_VERSION = 3;
    public static final int OVERRIDES_VERSION = 4;
    public static final int CURRENT_VERSION = OVERRIDES_VERSION;

    public Project {
        format = format == null || format.isBlank() ? FORMAT : format;
        version = version <= 0 ? CURRENT_VERSION : version;
        id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
        name = name == null ? "" : name;
        description = description == null ? "" : description;
        settings = settings == null ? RequestSettings.defaults() : settings;
        auth = auth == null || auth.isInherit() ? AuthConfig.none() : auth;
        headers = headers == null ? List.of() : List.copyOf(headers);
        variables = variables == null ? List.of() : List.copyOf(variables);
        environments = environments == null ? List.of() : List.copyOf(environments);
        items = items == null ? List.of() : List.copyOf(items);
    }

    public static Project create(String name) {
        return new Project(null, 0, null, name, null, null, null, null, null, null, null);
    }

    public Optional<Environment> findEnvironment(String environmentId) {
        return environments.stream()
                .filter(e -> e.id().equals(environmentId))
                .findFirst();
    }

    public Optional<ProjectItem> findItem(String itemId) {
        return Folder.of("", items).find(itemId);
    }
}