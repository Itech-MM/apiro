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
            Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS",
                    Endpoint.ANY_METHOD)));

    private static final Pattern PATH_VARIABLE = Pattern.compile("\\{([^/{}]+)}");

    public static final int MAX_SCHEMAS = 500;

    private ManifestValidator() {
    }

    public static List<String> validate(Manifest manifest) {
        return run(manifest, false);
    }

    public static List<String> validateCanonical(Manifest manifest) {
        return run(manifest, true);
    }

    private static List<String> run(Manifest manifest, boolean canonical) {
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
            checkEndpoint("endpoints[" + i + "]", endpoints.get(i), schemas, seenIds, problems, canonical);
        }
        for (Map.Entry<String, Schema> entry : schemas.entrySet()) {
            checkSchema("schemas." + entry.getKey(), entry.getValue(), schemas, problems,
                    new IdentityHashMap<Schema, Boolean>(), canonical);
        }
        if (canonical) {
            checkCanonical(manifest, problems);
        }
        return problems;
    }

    private static void checkCanonical(Manifest manifest, List<String> problems) {
        List<Endpoint> endpoints = manifest.getEndpoints();
        for (int i = 1; i < endpoints.size(); i++) {
            Endpoint previous = endpoints.get(i - 1);
            Endpoint current = endpoints.get(i);
            if (previous != null && current != null && compareRoute(previous, current) > 0) {
                problems.add("endpoints[" + i + "] is out of order: endpoints must be sorted by path, then method, then id");
            }
        }
        for (int i = 0; i < endpoints.size(); i++) {
            if (endpoints.get(i) != null) {
                checkCanonicalEndpoint("endpoints[" + i + "]", endpoints.get(i), problems);
            }
        }
        if (manifest.getSource() != null && !ManifestSource.ALL.contains(manifest.getSource())) {
            problems.add("source must be one of " + ManifestSource.ALL + " but was " + manifest.getSource());
        }
        Application application = manifest.getApplication();
        if (application != null && isBlank(application.getName()) && isBlank(application.getContextPath())
                && isBlank(application.getServletPath())) {
            problems.add("application is empty and must be omitted");
        }
        checkSorted("schemas", new ArrayList<>(manifest.getSchemas().keySet()), problems);
        if (manifest.getSchemas().size() > MAX_SCHEMAS) {
            problems.add("schemas has " + manifest.getSchemas().size() + " entries but the limit is " + MAX_SCHEMAS);
        }
    }

    private static void checkCanonicalEndpoint(String where, Endpoint endpoint, List<String> problems) {
        if (endpoint.getPattern() != null && endpoint.getPattern().equals(endpoint.getPath())) {
            problems.add(where + ".pattern equals path and must be omitted");
        }
        checkSorted(where + ".consumes", endpoint.getConsumes(), problems);
        checkSorted(where + ".produces", endpoint.getProduces(), problems);
        Conditions conditions = endpoint.getConditions();
        if (conditions != null) {
            if (conditions.getParams().isEmpty() && conditions.getHeaders().isEmpty()
                    && conditions.getUnsupported().isEmpty()) {
                problems.add(where + ".conditions is empty and must be omitted");
            }
            checkSorted(where + ".conditions.params", conditions.getParams(), problems);
            checkSorted(where + ".conditions.headers", conditions.getHeaders(), problems);
            checkSorted(where + ".conditions.unsupported", conditions.getUnsupported(), problems);
        }
        if (endpoint.getSecurity() != null) {
            checkSecurity(where + ".security", endpoint.getSecurity(), problems);
        }
    }

    private static void checkSecurity(String where, Security security, List<String> problems) {
        Authentication authentication = security.getAuthentication();
        Authorization authorization = security.getAuthorization();
        if (authentication == null && authorization == null) {
            problems.add(where + " is empty and must be omitted");
        }
        if (authentication != null) {
            if (!SecurityStatus.DECLARED.equals(authentication.getStatus())) {
                problems.add(where + ".authentication must be omitted unless its status is 'declared'");
            }
            checkSorted(where + ".authentication.schemes", authentication.getSchemes(), problems);
        }
        if (authorization != null) {
            if (!SecurityStatus.DECLARED.equals(authorization.getStatus())) {
                problems.add(where + ".authorization must be omitted unless its status is 'declared'");
            } else if (authorization.getAccess() == null || !AccessLevel.ALL.contains(authorization.getAccess())) {
                problems.add(where + ".authorization.access must be one of " + AccessLevel.ALL
                        + " but was " + authorization.getAccess());
            }
        }
    }

    private static void checkSorted(String where, List<String> values, List<String> problems) {
        for (int i = 1; i < values.size(); i++) {
            if (compareCodePoints(values.get(i - 1), values.get(i)) > 0) {
                problems.add(where + " is not sorted");
                return;
            }
        }
    }

    private static int compareCodePoints(String a, String b) {
        String left = nullToEmpty(a);
        String right = nullToEmpty(b);
        int i = 0;
        while (i < left.length() && i < right.length()) {
            int x = left.codePointAt(i);
            int y = right.codePointAt(i);
            if (x != y) {
                return x < y ? -1 : 1;
            }
            i += Character.charCount(x);
        }
        return left.length() - right.length();
    }

    private static int compareRoute(Endpoint a, Endpoint b) {
        int byPath = compareCodePoints(a.getPath(), b.getPath());
        if (byPath != 0) {
            return byPath;
        }
        int byMethod = compareCodePoints(a.getMethod(), b.getMethod());
        return byMethod != 0 ? byMethod : compareCodePoints(a.getId(), b.getId());
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static void checkEndpoint(String where, Endpoint endpoint, Map<String, Schema> schemas,
                                      Set<String> seenIds, List<String> problems,
                                      boolean canonical) {
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
                        new IdentityHashMap<Schema, Boolean>(), canonical);
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
                    new IdentityHashMap<Schema, Boolean>(), canonical);
        }
    }

    private static void checkSchema(String where, Schema schema, Map<String, Schema> schemas,
                                    List<String> problems, Map<Schema, Boolean> visiting, boolean canonical) {
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
            if (canonical && hasFieldsBesidesRef(schema)) {
                problems.add(where + " has fields besides 'ref': a schema with a ref must carry only the ref");
            }
        } else if (schema.getType() != null && !SchemaType.ALL.contains(schema.getType())) {
            problems.add(where + ".type must be one of " + SchemaType.ALL + " but was " + schema.getType());
        } else {
            checkShape(where, schema, problems);
        }
        if (schema.getItems() != null) {
            checkSchema(where + ".items", schema.getItems(), schemas, problems, visiting, canonical);
        }
        for (Map.Entry<String, Schema> property : schema.getProperties().entrySet()) {
            checkSchema(where + ".properties." + property.getKey(), property.getValue(), schemas, problems,
                    visiting, canonical);
        }
        visiting.remove(schema);
    }

    /** Fields must fit the declared type, and limits must not contradict each other. */
    private static void checkShape(String where, Schema schema, List<String> problems) {
        String type = schema.getType();
        if (type != null) {
            if (schema.getItems() != null && !SchemaType.ARRAY.equals(type)) {
                problems.add(where + ".items is only allowed on type 'array' but type is '" + type + "'");
            }
            if (!SchemaType.OBJECT.equals(type)) {
                if (!schema.getProperties().isEmpty()) {
                    problems.add(where + ".properties is only allowed on type 'object' but type is '" + type + "'");
                }
                if (!schema.getRequired().isEmpty()) {
                    problems.add(where + ".required is only allowed on type 'object' but type is '" + type + "'");
                }
            }
        }
        if (SchemaType.OBJECT.equals(type)) {
            for (String name : schema.getRequired()) {
                if (!schema.getProperties().containsKey(name)) {
                    problems.add(where + ".required names '" + name + "' which is not in properties");
                }
            }
        }
        if (schema.getMinimum() != null && schema.getMaximum() != null
                && schema.getMinimum() > schema.getMaximum()) {
            problems.add(where + ".minimum " + schema.getMinimum() + " is greater than maximum " + schema.getMaximum());
        }
        checkRange(where, "Length", schema.getMinLength(), schema.getMaxLength(), problems);
        checkRange(where, "Items", schema.getMinItems(), schema.getMaxItems(), problems);
    }

    private static void checkRange(String where, String what, Integer min, Integer max, List<String> problems) {
        if (min != null && min < 0) {
            problems.add(where + ".min" + what + " must not be negative but was " + min);
        }
        if (max != null && max < 0) {
            problems.add(where + ".max" + what + " must not be negative but was " + max);
        }
        if (min != null && max != null && min > max) {
            problems.add(where + ".min" + what + " " + min + " is greater than max" + what + " " + max);
        }
    }

    private static boolean hasFieldsBesidesRef(Schema schema) {
        return schema.getType() != null
                || schema.getTitle() != null
                || schema.getFormat() != null
                || schema.getItems() != null
                || !schema.getProperties().isEmpty()
                || !schema.getRequired().isEmpty()
                || !schema.getEnumValues().isEmpty()
                || schema.getNullable() != null
                || schema.getMinimum() != null
                || schema.getMaximum() != null
                || schema.getMinLength() != null
                || schema.getMaxLength() != null
                || schema.getPattern() != null
                || schema.getMinItems() != null
                || schema.getMaxItems() != null
                || schema.getExample() != null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
