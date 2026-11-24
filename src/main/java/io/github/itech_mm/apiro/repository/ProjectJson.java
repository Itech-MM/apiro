package io.github.itech_mm.apiro.repository;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import io.github.itech_mm.apiro.model.Project;
import io.github.itech_mm.apiro.model.ProjectItem;

public final class ProjectJson {

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .registerTypeAdapter(ProjectItem.class, new ProjectItemAdapter())
            .create();

    private ProjectJson() {
    }

    public static String toJson(Project project) {
        return GSON.toJson(project);
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
}