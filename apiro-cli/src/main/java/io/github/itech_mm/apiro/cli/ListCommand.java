package io.github.itech_mm.apiro.cli;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.itech_mm.apiro.model.Environment;
import io.github.itech_mm.apiro.model.Folder;
import io.github.itech_mm.apiro.model.Project;
import io.github.itech_mm.apiro.model.ProjectItem;
import io.github.itech_mm.apiro.model.RequestItem;
import io.github.itech_mm.apiro.model.ScenarioItem;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

final class ListCommand implements Command {

    private static final String JSON = "--json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    record ScenarioLine(String id, String name, int steps) {
    }

    record RequestLine(String id, String name, String method) {
    }

    record Listing(String project, int version, List<String> environments, List<ScenarioLine> scenarios,
                   List<RequestLine> requests) {
    }

    @Override
    public String name() {
        return "list";
    }

    @Override
    public String summary() {
        return "Show the environments, scenarios and requests in a project file";
    }

    @Override
    public String usage() {
        return """
                Usage: apiro list <project-file> [--json]

                Shows the environments, scenarios and requests of a project file.
                Names that sit in folders are shown as "Folder / Name".

                Options:
                  --json   Print the listing as JSON, including ids
                """;
    }

    @Override
    public Set<String> valueOptions() {
        return Set.of();
    }

    @Override
    public Set<String> flagOptions() {
        return Set.of(JSON);
    }

    @Override
    public int run(Arguments arguments, PrintStream out, PrintStream err) {
        if (arguments.positionals().size() != 1) {
            throw new CliException("Expected one project file");
        }
        Project project = ProjectFiles.open(arguments.positionals().get(0)).project();
        Listing listing = listing(project);
        if (arguments.flag(JSON)) {
            out.println(GSON.toJson(listing));
        } else {
            print(listing, out);
        }
        return 0;
    }

    private static Listing listing(Project project) {
        List<String> environments = new ArrayList<>();
        for (Environment environment : project.environments()) {
            environments.add(environment.name());
        }
        List<ScenarioLine> scenarios = new ArrayList<>();
        List<RequestLine> requests = new ArrayList<>();
        collect(project.items(), "", scenarios, requests);
        return new Listing(project.name(), project.version(), environments, scenarios, requests);
    }

    private static void collect(List<ProjectItem> items, String prefix, List<ScenarioLine> scenarios,
                                List<RequestLine> requests) {
        for (ProjectItem item : items) {
            if (item instanceof RequestItem request) {
                String method = request.method() == null ? "" : request.method().name();
                requests.add(new RequestLine(request.id(), prefix + request.name(), method));
            } else if (item instanceof ScenarioItem scenario) {
                scenarios.add(new ScenarioLine(scenario.id(), prefix + scenario.name(), scenario.steps().size()));
            } else if (item instanceof Folder folder) {
                collect(folder.items(), prefix + folder.name() + " / ", scenarios, requests);
            }
        }
    }

    private static void print(Listing listing, PrintStream out) {
        out.println("Project: " + listing.project() + " (file version " + listing.version() + ")");
        out.println();
        out.println("Environments");
        if (listing.environments().isEmpty()) {
            out.println("  (none)");
        }
        for (String environment : listing.environments()) {
            out.println("  " + environment);
        }
        out.println();
        out.println("Scenarios");
        if (listing.scenarios().isEmpty()) {
            out.println("  (none)");
        }
        for (ScenarioLine scenario : listing.scenarios()) {
            out.println("  " + scenario.name() + "  (" + scenario.steps() + (scenario.steps() == 1 ? " step)" : " steps)"));
        }
        out.println();
        out.println("Requests");
        if (listing.requests().isEmpty()) {
            out.println("  (none)");
        }
        for (RequestLine request : listing.requests()) {
            out.println(String.format("  %-7s %s", request.method(), request.name()));
        }
    }
}
