package io.github.itech_mm.apiro.spec;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ManifestValidator {
    private static final Set<String> METHODS = Collections.unmodifiableSet(new LinkedHashSet<>(
            Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS")));

    private static final Pattern PATH_VARIABLE = Pattern.compile("\\{([^/{}]+)}");

    private ManifestValidator() {
    }

    public static List<String> validate(Manifest manifest) {
        List<String> problems = new ArrayList<>();
        if (manifest == null) {
            problems.add("manifest is null");
            return problems;
        }
        if (manifest.getManifestVersion() < 1) {
            problems.add("manifestVersion must be 1 or higher");
        } else if (manifest.getManifestVersion() > ManifestVersion.CURRENT) {
            problems.add("manifestVersion " + manifest.getManifestVersion()
                    + " is newer than the supported version " + ManifestVersion.CURRENT);
        }
        if (manifest.getGenerator() == null || isBlank(manifest.getGenerator().getName())) {
            problems.add("generator.name is required");
        }

        Map<String, Schema> schemas = manifest.getSchemas();
        Set<String> seenIds = new HashSet<>();
        List<Endpoint> endpoints = manifest.getEndpoints();
        for (int i = 0; i < endpoints.size(); i++) {
            checkEndpoint("endpoints[" + i + "]", endpoints.get(i), schemas, seenIds, problems);
        }
        for (Map.Entry<String, Schema> entry : schemas.entrySet()) {
            checkSchema("schemas." + entry.getKey(), entry.getValue(), schemas, problems,
                    new IdentityHashMap<Schema, Boolean>());
        }
        return problems;
    }

    private static void checkEndpoint(String where, Endpoint endpoint, Map<String, Schema> schemas,
                                      Set<String> seenIds, List<String> problems) {
        if (endpoint == null) {
            problems.add(where + " is null");
            return;
        }
        if (isBlank(endpoint.getId())) {
            problems.add(where + ".id is required");
        } else if (!seenIds.add(endpoint.getId())) {
            problems.add(where + ".id is duplicated: " + endpoint.getId());
        }
        if (endpoint.getMethod() == null || !METHODS.contains(endpoint.getMethod())) {
            problems.add(where + ".method must be one of " + METHODS + " but was " + endpoint.getMethod());
        }
        String path = endpoint.getPath();
        if (isBlank(path) || !path.startsWith("/")) {
            problems.add(where + ".path must start with '/' but was " + path);
        }

        Set<String> templateNames = new LinkedHashSet<>();
        if (path != null) {
            Matcher matcher = PATH_VARIABLE.matcher(path);
            while (matcher.find()) {
                templateNames.add(matcher.group(1));
            }
        }
        Set<String> declaredPathNames = new LinkedHashSet<>();
        List<Parameter> parameters = endpoint.getParameters();
        for (int i = 0; i < parameters.size(); i++) {
            Parameter parameter = parameters.get(i);
            String parameterWhere = where + ".parameters[" + i + "]";
            if (parameter == null) {
                problems.add(parameterWhere + " is null");
                continue;
            }
            if (isBlank(parameter.getName())) {
                problems.add(parameterWhere + ".name is required");
            }
            if (parameter.getIn() == null || !ParameterLocation.ALL.contains(parameter.getIn())) {
                problems.add(parameterWhere + ".in must be one of " + ParameterLocation.ALL
                        + " but was " + parameter.getIn());
            } else if (ParameterLocation.PATH.equals(parameter.getIn())) {
                declaredPathNames.add(parameter.getName());
                if (!parameter.isRequired()) {
                    problems.add(parameterWhere + " is a path parameter and must be required");
                }
            }
            if (parameter.getSchema() != null) {
                checkSchema(parameterWhere + ".schema", parameter.getSchema(), schemas, problems,
                        new IdentityHashMap<Schema, Boolean>());
            }
        }
        for (String name : templateNames) {
            if (!declaredPathNames.contains(name)) {
                problems.add(where + " path variable {" + name + "} has no 'path' parameter");
            }
        }
        for (String name : declaredPathNames) {
            if (!templateNames.contains(name)) {
                problems.add(where + " path parameter '" + name + "' is not in the path " + path);
            }
        }
        if (endpoint.getBody() != null && endpoint.getBody().getSchema() != null) {
            checkSchema(where + ".body.schema", endpoint.getBody().getSchema(), schemas, problems,
                    new IdentityHashMap<Schema, Boolean>());
        }
    }

    private static void checkSchema(String where, Schema schema, Map<String, Schema> schemas,
                                    List<String> problems, Map<Schema, Boolean> visiting) {
        if (schema == null) {
            problems.add(where + " is null");
            return;
        }
        if (visiting.put(schema, Boolean.TRUE) != null) {
            return;
        }
        if (!isBlank(schema.getRef())) {
            if (!schemas.containsKey(schema.getRef())) {
                problems.add(where + " refers to unknown schema '" + schema.getRef() + "'");
            }
        } else if (schema.getType() != null && !SchemaType.ALL.contains(schema.getType())) {
            problems.add(where + ".type must be one of " + SchemaType.ALL + " but was " + schema.getType());
        }
        if (schema.getItems() != null) {
            checkSchema(where + ".items", schema.getItems(), schemas, problems, visiting);
        }
        for (Map.Entry<String, Schema> property : schema.getProperties().entrySet()) {
            checkSchema(where + ".properties." + property.getKey(), property.getValue(), schemas, problems, visiting);
        }
        visiting.remove(schema);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
