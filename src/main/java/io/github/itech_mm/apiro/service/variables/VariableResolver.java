package io.github.itech_mm.apiro.service.variables;

import io.github.itech_mm.apiro.model.Folder;
import io.github.itech_mm.apiro.model.Project;
import io.github.itech_mm.apiro.model.ProjectItem;
import io.github.itech_mm.apiro.model.Variable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class VariableResolver {

	public record Reference(int start, int end, String name, boolean defined) {
	}

	private static final Pattern REFERENCE = Pattern.compile("\\$\\{\\s*([A-Za-z0-9_.\\-]+)\\s*\\}");
	private static final int MAX_DEPTH = 8;

	private final Map<String, String> values;

	private VariableResolver(Map<String, String> values) {
		this.values = values;
	}

	public static VariableResolver of(List<List<Variable>> layersHighestFirst) {
		Map<String, String> values = new LinkedHashMap<>();
		for (List<Variable> layer : layersHighestFirst) {
			for (Variable variable : layer) {
				if (variable.enabled() && !variable.key().isBlank()) {
					values.putIfAbsent(variable.key(), variable.value());
				}
			}
		}
		return new VariableResolver(Collections.unmodifiableMap(values));
	}

	public static VariableResolver forItem(Project project, String environmentId, String itemId) {
		List<List<Variable>> layers = new ArrayList<>();
		List<Folder> path = folderPath(project.items(), itemId).orElse(List.of());
		for (int i = path.size() - 1; i >= 0; i--) {
			layers.add(path.get(i).variables());
		}
		project.findEnvironment(environmentId).ifPresent(environment -> layers.add(environment.variables()));
		layers.add(project.variables());
		return of(layers);
	}

	public Optional<String> value(String name) {
		return Optional.ofNullable(values.get(name));
	}

	public boolean isDefined(String name) {
		return values.containsKey(name);
	}

	public List<String> names() {
		return List.copyOf(values.keySet());
	}

	public List<Reference> scan(String text) {
		List<Reference> references = new ArrayList<>();
		if (text == null) {
			return references;
		}
		Matcher matcher = REFERENCE.matcher(text);
		while (matcher.find()) {
			String name = matcher.group(1);
			references.add(new Reference(matcher.start(), matcher.end(), name, values.containsKey(name)));
		}
		return references;
	}

	public String resolve(String text) {
		return resolve(text == null ? "" : text, 0);
	}

	private String resolve(String text, int depth) {
		if (text.isEmpty() || depth >= MAX_DEPTH) {
			return text;
		}
		Matcher matcher = REFERENCE.matcher(text);
		StringBuilder out = new StringBuilder();
		int last = 0;
		boolean replaced = false;
		while (matcher.find()) {
			String value = values.get(matcher.group(1));
			out.append(text, last, matcher.start());
			if (value == null) {
				out.append(matcher.group());
			} else {
				out.append(value);
				replaced = true;
			}
			last = matcher.end();
		}
		out.append(text, last, text.length());
		String result = out.toString();
		return replaced ? resolve(result, depth + 1) : result;
	}

	private static Optional<List<Folder>> folderPath(List<ProjectItem> items, String itemId) {
		for (ProjectItem item : items) {
			if (item.id().equals(itemId)) {
				List<Folder> found = new ArrayList<>();
				if (item instanceof Folder folder) {
					found.add(folder);
				}
				return Optional.of(found);
			}
			if (item instanceof Folder folder) {
				Optional<List<Folder>> inner = folderPath(folder.items(), itemId);
				if (inner.isPresent()) {
					inner.get().add(0, folder);
					return inner;
				}
			}
		}
		return Optional.empty();
	}
}