package io.github.itech_mm.apiro.repository;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import io.github.itech_mm.apiro.model.Folder;
import io.github.itech_mm.apiro.model.Project;
import io.github.itech_mm.apiro.model.ProjectItem;
import io.github.itech_mm.apiro.model.ScenarioItem;
import io.github.itech_mm.apiro.model.Step;
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
        int version = requiredVersion(project.items(), Project.BASE_VERSION);
        if (version == project.version()) {
            return project;
        }
        return new Project(project.format(), version, project.id(), project.name(), project.description(),
                project.settings(), project.auth(), project.headers(), project.variables(),
                project.environments(), project.items());
    }

    private static int requiredVersion(List<ProjectItem> items, int found) {
        int version = found;
        for (ProjectItem item : items) {
            if (item instanceof ScenarioItem scenario) {
                version = Math.max(version, Project.SCENARIO_VERSION);
                for (Step step : scenario.steps()) {
                    if (!step.headers().isEmpty()) {
                        version = Math.max(version, Project.HEADERS_VERSION);
                    }
                    if (step.bodyOverride() != null) {
                        version = Math.max(version, Project.OVERRIDES_VERSION);
                    }
                }
            } else if (item instanceof Folder folder) {
                version = requiredVersion(folder.items(), version);
            }
        }
        return version;
    }
}
