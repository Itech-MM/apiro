package io.github.itech_mm.apiro.ui.settings;

import io.github.itech_mm.apiro.model.AuthConfig;
import io.github.itech_mm.apiro.model.Environment;
import io.github.itech_mm.apiro.model.KeyValue;
import io.github.itech_mm.apiro.model.Project;
import io.github.itech_mm.apiro.model.RequestSettings;
import io.github.itech_mm.apiro.model.Variable;
import java.util.List;

public final class ProjectDraft {

    private String name;

    private String description;

    private RequestSettings settings;

    private AuthConfig auth;

    private List<KeyValue> headers;

    private List<Variable> variables;

    private List<Environment> environments;

    public ProjectDraft(Project source) {
        name = source.name();
        description = source.description();
        settings = source.settings();
        auth = source.auth();
        headers = source.headers();
        variables = source.variables();
        environments = source.environments();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public RequestSettings getSettings() {
        return settings;
    }

    public void setSettings(RequestSettings settings) {
        this.settings = settings;
    }

    public AuthConfig getAuth() {
        return auth;
    }

    public void setAuth(AuthConfig auth) {
        this.auth = auth;
    }

    public List<KeyValue> getHeaders() {
        return headers;
    }

    public void setHeaders(List<KeyValue> headers) {
        this.headers = headers;
    }

    public List<Variable> getVariables() {
        return variables;
    }

    public void setVariables(List<Variable> variables) {
        this.variables = variables;
    }

    public List<Environment> getEnvironments() {
        return environments;
    }

    public void setEnvironments(List<Environment> environments) {
        this.environments = environments;
    }

    public Project applyTo(Project current) {
        String finalName = name == null || name.isBlank() ? current.name() : name.trim();
        return new Project(current.format(), current.version(), current.id(), finalName, description,
                settings, auth, headers, variables, environments, current.items());
    }
}