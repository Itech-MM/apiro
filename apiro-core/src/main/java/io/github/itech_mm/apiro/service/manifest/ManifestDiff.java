package io.github.itech_mm.apiro.service.manifest;

import io.github.itech_mm.apiro.model.AuthConfig;
import io.github.itech_mm.apiro.model.RequestItem;
import io.github.itech_mm.apiro.model.Variable;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The result of comparing a manifest with a project. {@code scope} is the set of folder ids that were compared
 * (the empty string stands for the project root), or null when the whole project was compared.
 */
public record ManifestDiff(List<Updated> updated, List<Added> added, List<Removed> removed, int unchanged,
                           List<String> warnings, Set<String> scope) {

    public enum Match {
        ID, ROUTE, HANDLER
    }

    public record Updated(String requestId, String requestName, Match match, List<String> changes,
                          RequestItem request, List<Variable> variables) {

        public Updated {
            changes = List.copyOf(changes);
            variables = List.copyOf(variables);
        }

        public String key() {
            return requestId;
        }
    }

    public record Added(String endpointId, String folderName, RequestItem request, List<Variable> variables,
                        AuthConfig folderAuth) {

        public Added {
            variables = List.copyOf(variables);
        }

        public String key() {
            return endpointId;
        }
    }

    public record Removed(String requestId, String requestName, String endpointId) {
    }

    public ManifestDiff {
        updated = List.copyOf(updated);
        added = List.copyOf(added);
        removed = List.copyOf(removed);
        warnings = List.copyOf(warnings);
        scope = scope == null ? null : Set.copyOf(scope);
    }

    public boolean hasChanges() {
        return !updated.isEmpty() || !added.isEmpty() || !removed.isEmpty();
    }

    public Set<String> applicableKeys() {
        Set<String> keys = new LinkedHashSet<>();
        updated.forEach(entry -> keys.add(entry.key()));
        added.forEach(entry -> keys.add(entry.key()));
        return keys;
    }
}
