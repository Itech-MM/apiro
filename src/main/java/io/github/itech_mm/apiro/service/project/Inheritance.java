package io.github.itech_mm.apiro.service.project;

import io.github.itech_mm.apiro.model.AuthConfig;
import io.github.itech_mm.apiro.model.Folder;
import io.github.itech_mm.apiro.model.KeyValue;
import io.github.itech_mm.apiro.model.Project;
import io.github.itech_mm.apiro.model.ProjectItem;
import io.github.itech_mm.apiro.model.RequestItem;
import io.github.itech_mm.apiro.model.RequestSettings;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class Inheritance {

    public record Effective(AuthConfig auth, RequestSettings settings, List<KeyValue> headers) {
    }

    private Inheritance() {
    }

    public static Optional<Effective> forItem(Project project, String itemId) {
        return path(project.items(), itemId).map(chain -> build(project, chain));
    }

    private static Effective build(Project project, List<ProjectItem> chain) {
        AuthConfig auth = project.auth();
        RequestSettings settings = project.settings();
        Map<String, KeyValue> headers = new LinkedHashMap<>();
        applyHeaders(headers, project.headers());
        for (ProjectItem item : chain) {
            AuthConfig itemAuth = authOf(item);
            if (itemAuth != null && !itemAuth.isInherit()) {
                auth = itemAuth;
            }
            settings = merge(settings, settingsOf(item));
            applyHeaders(headers, headersOf(item));
        }
        return new Effective(auth, settings.withDefaults(), List.copyOf(headers.values()));
    }

    private static void applyHeaders(Map<String, KeyValue> target, List<KeyValue> layer) {
        for (KeyValue header : layer) {
            String key = header.key().trim().toLowerCase(Locale.ROOT);
            if (key.isEmpty()) {
                continue;
            }
            if (header.enabled()) {
                target.put(key, header);
            } else {
                target.remove(key);
            }
        }
    }

    private static RequestSettings merge(RequestSettings parent, RequestSettings child) {
        if (child == null) {
            return parent;
        }
        return new RequestSettings(
                pick(child.connectTimeoutMillis(), parent.connectTimeoutMillis()),
                pick(child.socketTimeoutMillis(), parent.socketTimeoutMillis()),
                pick(child.followRedirects(), parent.followRedirects()),
                pick(child.maxRedirects(), parent.maxRedirects()),
                pick(child.proxyHost(), parent.proxyHost()),
                pick(child.proxyPort(), parent.proxyPort()));
    }

    private static <T> T pick(T child, T parent) {
        return child != null ? child : parent;
    }

    private static AuthConfig authOf(ProjectItem item) {
        return item instanceof Folder folder ? folder.auth() : ((RequestItem) item).auth();
    }

    private static RequestSettings settingsOf(ProjectItem item) {
        return item instanceof Folder folder ? folder.settings() : ((RequestItem) item).settings();
    }

    private static List<KeyValue> headersOf(ProjectItem item) {
        return item instanceof Folder folder ? folder.headers() : ((RequestItem) item).headers();
    }

    private static Optional<List<ProjectItem>> path(List<ProjectItem> items, String id) {
        for (ProjectItem item : items) {
            if (item.id().equals(id)) {
                List<ProjectItem> found = new ArrayList<>();
                found.add(item);
                return Optional.of(found);
            }
            if (item instanceof Folder folder) {
                Optional<List<ProjectItem>> inner = path(folder.items(), id);
                if (inner.isPresent()) {
                    inner.get().add(0, folder);
                    return inner;
                }
            }
        }
        return Optional.empty();
    }
}