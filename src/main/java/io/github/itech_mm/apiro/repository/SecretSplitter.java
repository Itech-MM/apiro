package io.github.itech_mm.apiro.repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.itech_mm.apiro.model.Environment;
import io.github.itech_mm.apiro.model.Project;
import io.github.itech_mm.apiro.model.RequestItem;
import io.github.itech_mm.apiro.model.Variable;

public final class SecretSplitter {

    public record Split(Project project, LocalState state) {
    }

    private SecretSplitter() {
    }

    public static Split split(Project project, LocalState state) {
    	LocalState next = new LocalState(state.activeEnvironmentId(), null, keptRuns(project, state),
    	        keptDrafts(project, state));
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
    
    private static Map<String, LastRun> keptRuns(Project project, LocalState state) {
        Map<String, LastRun> kept = new LinkedHashMap<>();
        state.lastRuns().forEach((requestId, run) -> {
            if (project.findItem(requestId).isPresent()) {
                kept.put(requestId, run);
            }
        });
        return kept;
    }
    
    private static Map<String, RequestItem> keptDrafts(Project project, LocalState state) {
        Map<String, RequestItem> kept = new LinkedHashMap<>();
        state.drafts().forEach((requestId, draft) -> {
            if (project.findItem(requestId).filter(RequestItem.class::isInstance).isPresent()) {
                kept.put(requestId, draft);
            }
        });
        return kept;
    }
}