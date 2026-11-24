package io.github.itech_mm.apiro.repository;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import io.github.itech_mm.apiro.model.Folder;
import io.github.itech_mm.apiro.model.Project;
import io.github.itech_mm.apiro.model.ProjectItem;
import io.github.itech_mm.apiro.model.ScenarioItem;
import java.util.List;

public final class ProjectJson {

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .registerTypeAdapter(ProjectItem.class, new ProjectItemAdapter())
            .create();

    private ProjectJson() {
    }

    public static String toJson(Project project) {
        return GSON.toJson(stamped(project));
    }

    public static Project fromJson(String json) {
        Project project = GSON.fromJson(json, Project.class);
        if (project == null) {
            throw new JsonParseException("Project JSON is empty");
        }
        if (!Project.FORMAT.equals(project.format())) {
            throw new JsonParseException("Not an Apiro project file");
        }
        if (project.version() > Project.CURRENT_VERSION) {
            throw new JsonParseException("Project file was saved by a newer version of Apiro");
        }
        return project;
    }

    private static Project stamped(Project project) {
        int version = hasScenario(project.items()) ? Project.CURRENT_VERSION : Project.BASE_VERSION;
        if (version == project.version()) {
            return project;
        }
        return new Project(project.format(), version, project.id(), project.name(), project.description(),
                project.settings(), project.auth(), project.headers(), project.variables(),
                project.environments(), project.items());
    }

    private static boolean hasScenario(List<ProjectItem> items) {
        for (ProjectItem item : items) {
            if (item instanceof ScenarioItem) {
                return true;
            }
            if (item instanceof Folder folder && hasScenario(folder.items())) {
                return true;
            }
        }
        return false;
    }
}