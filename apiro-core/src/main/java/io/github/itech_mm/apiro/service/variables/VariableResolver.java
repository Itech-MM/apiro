package io.github.itech_mm.apiro.service.variables;

import io.github.itech_mm.apiro.model.Folder;
import io.github.itech_mm.apiro.model.Project;
import io.github.itech_mm.apiro.model.ProjectItem;
import io.github.itech_mm.apiro.model.Variable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class VariableResolver {

	public record Reference(int start, int end, String name, boolean defined) {
	}

	public static final Pattern REFERENCE = Pattern.compile("\\$\\{\\s*(\\$?[A-Za-z0-9_.\\-]+)\\s*\\}");
	private static final int MAX_DEPTH = 8;

	private final Map<String, String> values;
	private final Set<String> literal;
	private final DynamicVariables dynamic;

	private VariableResolver(Map<String, String> values, Set<String> literal, DynamicVariables dynamic) {
		this.values = values;
		this.literal = literal;
		this.dynamic = dynamic;
	}

	public static VariableResolver of(List<List<Variable>> layersHighestFirst) {
		return of(layersHighestFirst, DynamicVariables.system());
	}

	public static VariableResolver of(List<List<Variable>> layersHighestFirst, DynamicVariables dynamic) {
		return of(layersHighestFirst, Set.of(), dynamic);
	}

	private static VariableResolver of(List<List<Variable>> layersHighestFirst, Set<String> literal,
			DynamicVariables dynamic) {
		Map<String, String> values = new LinkedHashMap<>();
		for (List<Variable> layer : layersHighestFirst) {
			for (Variable variable : layer) {
				if (variable.enabled() && !variable.key().isBlank()) {
					values.putIfAbsent(variable.key(), variable.value());
				}
			}
		}
		return new VariableResolver(Collections.unmodifiableMap(values), Set.copyOf(literal), dynamic);
	}

	public static VariableResolver forItem(Project project, String environmentId, String itemId) {
		return forItem(project, environmentId, itemId, List.of());
	}

	public static VariableResolver forItem(Project project, String environmentId, String itemId,
			List<Variable> runtime) {
		return forItem(project, environmentId, itemId, runtime, List.of());
	}

	public static VariableResolver forItem(Project project, String environmentId, String itemId,
			List<Variable> runtime, List<Variable> literal) {
		List<List<Variable>> layers = new ArrayList<>();
		layers.add(literal);
		layers.add(runtime);
		List<Folder> path = folderPath(project.items(), itemId).orElse(List.of());
		for (int i = path.size() - 1; i >= 0; i--) {
			layers.add(path.get(i).variables());
		}
		project.findEnvironment(environmentId).ifPresent(environment -> layers.add(environment.variables()));
		layers.add(project.variables());
		Set<String> literalNames = new HashSet<>();
		for (Variable variable : literal) {
			if (variable.enabled() && !variable.key().isBlank()) {
				literalNames.add(variable.key());
			}
		}
		return of(layers, literalNames, DynamicVariables.system());
	}

	public Optional<String> value(String name) {
		return Optional.ofNullable(lookup(name));
	}

	public boolean isDefined(String name) {
		return values.containsKey(name) || DynamicVariables.isKnown(name);
	}

	private String lookup(String name) {
		String value = values.get(name);
		if (value != null || !DynamicVariables.isDynamic(name)) {
			return value;
		}
		return dynamic.generate(name).orElse(null);
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
			references.add(new Reference(matcher.start(), matcher.end(), name, isDefined(name)));
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
		while (matcher.find()) {
			String name = matcher.group(1);
			String value = lookup(name);
			out.append(text, last, matcher.start());
			if (value == null) {
				out.append(matcher.group());
			} else if (literal.contains(name)) {
				out.append(value);
			} else {
				out.append(resolve(value, depth + 1));
			}
			last = matcher.end();
		}
		out.append(text, last, text.length());
		return out.toString();
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