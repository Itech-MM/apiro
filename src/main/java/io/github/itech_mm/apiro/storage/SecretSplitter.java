package io.github.itech_mm.apiro.storage;

import io.github.itech_mm.apiro.model.Environment;
import io.github.itech_mm.apiro.model.Project;
import io.github.itech_mm.apiro.model.Variable;
import java.util.ArrayList;
import java.util.List;

public final class SecretSplitter {

    public record Split(Project project, LocalState state) {
    }

    private SecretSplitter() {
    }

    public static Split split(Project project, LocalState state) {
        LocalState next = new LocalState(state.activeEnvironmentId(), null);
        List<Environment> environments = new ArrayList<>();
        for (Environment environment : project.environments()) {
            List<Variable> variables = new ArrayList<>();
            for (Variable variable : environment.variables()) {
                if (variable.secret()) {
                    next = next.withSecret(environment.id(), variable.key(), variable.value());
                    variables.add(new Variable(variable.key(), "", variable.enabled(), true));
                } else {
                    variables.add(variable);
                }
            }
            environments.add(new Environment(environment.id(), environment.name(), variables));
        }
        return new Split(withEnvironments(project, environments), next);
    }

    public static Project merge(Project project, LocalState state) {
        List<Environment> environments = new ArrayList<>();
        for (Environment environment : project.environments()) {
            List<Variable> variables = new ArrayList<>();
            for (Variable variable : environment.variables()) {
                if (variable.secret()) {
                    String value = state.secret(environment.id(), variable.key());
                    variables.add(new Variable(variable.key(), value, variable.enabled(), true));
                } else {
                    variables.add(variable);
                }
            }
            environments.add(new Environment(environment.id(), environment.name(), variables));
        }
        return withEnvironments(project, environments);
    }

    private static Project withEnvironments(Project project, List<Environment> environments) {
        return new Project(project.format(), project.version(), project.id(), project.name(),
                project.description(), project.settings(), project.auth(), project.headers(),
                project.variables(), environments, project.items());
    }
}