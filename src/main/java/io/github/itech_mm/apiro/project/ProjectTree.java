package io.github.itech_mm.apiro.project;

import io.github.itech_mm.apiro.model.Folder;
import io.github.itech_mm.apiro.model.Project;
import io.github.itech_mm.apiro.model.ProjectItem;
import io.github.itech_mm.apiro.model.RequestItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;

public final class ProjectTree {

    private ProjectTree() {
    }

    public static Project withRequest(Project project, RequestItem updated) {
        return rebuilt(project, replace(project.items(), updated));
    }

    public static Project withAdded(Project project, String parentId, ProjectItem added) {
        String parent = isFolder(project, parentId) ? parentId : "";
        return rebuilt(project, inFolder(project.items(), parent, list -> placed(list, added, "", false)));
    }

    public static Project withInsertedAfter(Project project, String anchorId, ProjectItem added) {
        String parent = parentOf(project, anchorId).orElse("");
        return rebuilt(project, inFolder(project.items(), parent, list -> placed(list, added, anchorId, true)));
    }

    public static Project withRenamed(Project project, String id, String name) {
        return rebuilt(project, renamed(project.items(), id, name));
    }

    public static Project without(Project project, String id) {
        return rebuilt(project, removed(project.items(), id));
    }

    public static Project withMoved(Project project, String id, String parentId, String anchorId, boolean after) {
        if (!isValidMove(project, id, parentId, anchorId)) {
            return project;
        }
        String parent = parentId == null ? "" : parentId;
        ProjectItem item = project.findItem(id).orElseThrow();
        Project stripped = without(project, id);
        return rebuilt(stripped, inFolder(stripped.items(), parent, list -> placed(list, item, anchorId, after)));
    }

    public static boolean isValidMove(Project project, String id, String parentId, String anchorId) {
        Optional<ProjectItem> found = project.findItem(id);
        if (found.isEmpty()) {
            return false;
        }
        String parent = parentId == null ? "" : parentId;
        String anchor = anchorId == null ? "" : anchorId;
        if (id.equals(anchor)) {
            return false;
        }
        if (!parent.isEmpty() && !isFolder(project, parent)) {
            return false;
        }
        if (!parent.isEmpty() && found.get() instanceof Folder folder
                && (folder.id().equals(parent) || folder.find(parent).isPresent())) {
            return false;
        }
        if (!anchor.isEmpty()) {
            Optional<String> anchorParent = parentOf(project, anchor);
            if (anchorParent.isEmpty() || !anchorParent.get().equals(parent)) {
                return false;
            }
        }
        return true;
    }

    public static ProjectItem copyOf(ProjectItem item) {
        return copy(item, true);
    }

    public static int requestCount(ProjectItem item) {
        if (item instanceof Folder folder) {
            int count = 0;
            for (ProjectItem child : folder.items()) {
                count += requestCount(child);
            }
            return count;
        }
        return 1;
    }

    public static Optional<String> parentOf(Project project, String itemId) {
        return parentOf(project.items(), itemId, "");
    }

    private static Optional<String> parentOf(List<ProjectItem> items, String itemId, String parentId) {
        for (ProjectItem item : items) {
            if (item.id().equals(itemId)) {
                return Optional.of(parentId);
            }
            if (item instanceof Folder folder) {
                Optional<String> found = parentOf(folder.items(), itemId, folder.id());
                if (found.isPresent()) {
                    return found;
                }
            }
        }
        return Optional.empty();
    }

    private static boolean isFolder(Project project, String id) {
        return id != null && !id.isEmpty() && project.findItem(id).filter(Folder.class::isInstance).isPresent();
    }

    private static Project rebuilt(Project project, List<ProjectItem> items) {
        return new Project(project.format(), project.version(), project.id(), project.name(),
                project.description(), project.settings(), project.auth(), project.headers(),
                project.variables(), project.environments(), items);
    }

    private static Folder withItems(Folder folder, List<ProjectItem> items) {
        return new Folder(folder.id(), folder.name(), folder.auth(), folder.headers(), folder.variables(),
                folder.settings(), items);
    }

    private static ProjectItem withName(ProjectItem item, String name) {
        if (item instanceof Folder folder) {
            return new Folder(folder.id(), name, folder.auth(), folder.headers(), folder.variables(),
                    folder.settings(), folder.items());
        }
        RequestItem request = (RequestItem) item;
        return new RequestItem(request.id(), name, request.method(), request.url(), request.query(),
                request.headers(), request.auth(), request.body(), request.settings());
    }

    private static ProjectItem copy(ProjectItem item, boolean top) {
        String name = top ? item.name() + " copy" : item.name();
        if (item instanceof Folder folder) {
            List<ProjectItem> children = new ArrayList<>();
            for (ProjectItem child : folder.items()) {
                children.add(copy(child, false));
            }
            return new Folder(null, name, folder.auth(), folder.headers(), folder.variables(),
                    folder.settings(), children);
        }
        RequestItem request = (RequestItem) item;
        return new RequestItem(null, name, request.method(), request.url(), request.query(),
                request.headers(), request.auth(), request.body(), request.settings());
    }

    private static List<ProjectItem> placed(List<ProjectItem> list, ProjectItem added, String anchorId, boolean after) {
        List<ProjectItem> result = new ArrayList<>(list);
        int index = result.size();
        if (anchorId != null && !anchorId.isEmpty()) {
            for (int i = 0; i < result.size(); i++) {
                if (result.get(i).id().equals(anchorId)) {
                    index = after ? i + 1 : i;
                    break;
                }
            }
        }
        result.add(index, added);
        return result;
    }

    private static List<ProjectItem> inFolder(List<ProjectItem> items, String parentId,
                                              UnaryOperator<List<ProjectItem>> edit) {
        if (parentId == null || parentId.isEmpty()) {
            return edit.apply(items);
        }
        List<ProjectItem> result = new ArrayList<>(items.size());
        for (ProjectItem item : items) {
            if (item instanceof Folder folder) {
                if (folder.id().equals(parentId)) {
                    result.add(withItems(folder, edit.apply(folder.items())));
                } else {
                    result.add(withItems(folder, inFolder(folder.items(), parentId, edit)));
                }
            } else {
                result.add(item);
            }
        }
        return result;
    }

    private static List<ProjectItem> replace(List<ProjectItem> items, RequestItem updated) {
        List<ProjectItem> result = new ArrayList<>(items.size());
        for (ProjectItem item : items) {
            if (item.id().equals(updated.id())) {
                result.add(updated);
            } else if (item instanceof Folder folder) {
                result.add(withItems(folder, replace(folder.items(), updated)));
            } else {
                result.add(item);
            }
        }
        return result;
    }

    private static List<ProjectItem> renamed(List<ProjectItem> items, String id, String name) {
        List<ProjectItem> result = new ArrayList<>(items.size());
        for (ProjectItem item : items) {
            if (item.id().equals(id)) {
                result.add(withName(item, name));
            } else if (item instanceof Folder folder) {
                result.add(withItems(folder, renamed(folder.items(), id, name)));
            } else {
                result.add(item);
            }
        }
        return result;
    }

    private static List<ProjectItem> removed(List<ProjectItem> items, String id) {
        List<ProjectItem> result = new ArrayList<>(items.size());
        for (ProjectItem item : items) {
            if (item.id().equals(id)) {
                continue;
            }
            if (item instanceof Folder folder) {
                result.add(withItems(folder, removed(folder.items(), id)));
            } else {
                result.add(item);
            }
        }
        return result;
    }
}