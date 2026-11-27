package io.github.itech_mm.apiro.service.manifest;

import io.github.itech_mm.apiro.model.AuthConfig;
import io.github.itech_mm.apiro.model.AuthType;
import io.github.itech_mm.apiro.model.BodyType;
import io.github.itech_mm.apiro.model.Folder;
import io.github.itech_mm.apiro.model.FormPart;
import io.github.itech_mm.apiro.model.HttpMethod;
import io.github.itech_mm.apiro.model.KeyValue;
import io.github.itech_mm.apiro.model.PartKind;
import io.github.itech_mm.apiro.model.Project;
import io.github.itech_mm.apiro.model.ProjectItem;
import io.github.itech_mm.apiro.model.RequestBody;
import io.github.itech_mm.apiro.model.RequestItem;
import io.github.itech_mm.apiro.model.Variable;
import io.github.itech_mm.apiro.service.project.ProjectTree;
import io.github.itech_mm.apiro.spec.AccessLevel;
import io.github.itech_mm.apiro.spec.Application;
import io.github.itech_mm.apiro.spec.Authentication;
import io.github.itech_mm.apiro.spec.Authorization;
import io.github.itech_mm.apiro.spec.Endpoint;
import io.github.itech_mm.apiro.spec.Manifest;
import io.github.itech_mm.apiro.spec.Parameter;
import io.github.itech_mm.apiro.spec.ParameterLocation;
import io.github.itech_mm.apiro.spec.Schema;
import io.github.itech_mm.apiro.spec.SchemaType;
import io.github.itech_mm.apiro.spec.Security;
import io.github.itech_mm.apiro.spec.SecurityStatus;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ManifestImporter {

    public static final String BASE_URL_VARIABLE = "baseUrl";

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([^/{}]+)}");
    private static final Pattern VARIABLE_NAME = Pattern.compile("[A-Za-z0-9_.\\-]+");
    private static final String DEFAULT_GROUP = "Endpoints";

    public record Result(List<Folder> folders, List<String> warnings, int requestCount, Set<String> anyMethodIds) {

        public Result {
            folders = List.copyOf(folders);
            warnings = List.copyOf(warnings);
            anyMethodIds = Set.copyOf(anyMethodIds);
        }
    }

    private ManifestImporter() {
    }

    public static Result importManifest(Manifest manifest) {
        List<String> warnings = new ArrayList<>();
        Map<String, List<Endpoint>> groups = new LinkedHashMap<>();
        String fallbackGroup = fallbackGroup(manifest.getApplication());
        Set<String> anyMethodIds = new HashSet<>();
        for (Endpoint endpoint : manifest.getEndpoints()) {
            String problem = unusable(endpoint);
            if (problem != null) {
                warnings.add("Skipped " + label(endpoint) + ": " + problem);
                continue;
            }
            if (isAnyMethod(endpoint)) {
                warnings.add("ANY " + endpoint.getPath() + ": accepts any method, imported as GET");
                if (!isBlank(endpoint.getId())) {
                    anyMethodIds.add(endpoint.getId());
                }
            }
            String group = isBlank(endpoint.getGroup()) ? fallbackGroup : endpoint.getGroup();
            groups.computeIfAbsent(group, key -> new ArrayList<>()).add(endpoint);
        }
        String prefix = prefix(manifest.getApplication());
        List<Folder> folders = new ArrayList<>();
        int count = 0;
        for (Map.Entry<String, List<Endpoint>> group : groups.entrySet()) {
            Folder folder = folder(group.getKey(), group.getValue(), prefix, manifest.getSchemas(), warnings);
            folders.add(folder);
            count += group.getValue().size();
        }
        return new Result(folders, warnings, count, anyMethodIds);
    }

    public static boolean hasBaseUrl(Project project) {
        boolean inProject = project.variables().stream()
                .anyMatch(v -> v.enabled() && v.key().equals(BASE_URL_VARIABLE));
        return inProject || project.environments().stream().anyMatch(e -> e.find(BASE_URL_VARIABLE).isPresent());
    }

    public static Project withBaseUrl(Project project, String baseUrl) {
        if (isBlank(baseUrl) || hasBaseUrl(project)) {
            return project;
        }
        List<Variable> variables = new ArrayList<>(project.variables());
        variables.add(Variable.of(BASE_URL_VARIABLE, baseUrl.trim()));
        return new Project(project.format(), project.version(), project.id(), project.name(), project.description(),
                project.settings(), project.auth(), project.headers(), variables, project.environments(),
                project.items());
    }

    public static Project addTo(Project project, Result result) {
        Project updated = project;
        for (Folder folder : result.folders()) {
            updated = ProjectTree.withAdded(updated, "", folder);
        }
        return updated;
    }

    private static Folder folder(String name, List<Endpoint> endpoints, String prefix, Map<String, Schema> schemas,
            List<String> warnings) {
        Map<String, Variable> variables = new LinkedHashMap<>();
        Set<String> usedNames = new HashSet<>();
        AuthType folderAuth = folderAuth(name, endpoints, warnings);
        List<ProjectItem> items = new ArrayList<>();
        int skippedCookies = 0;
        for (Endpoint endpoint : endpoints) {
            skippedCookies += (int) endpoint.getParameters().stream()
                    .filter(p -> ParameterLocation.COOKIE.equals(p.getIn())).count();
            String url = "${" + BASE_URL_VARIABLE + "}" + prefix + url(endpoint.getPath(), variables, warnings, endpoint);
            items.add(new RequestItem(null, uniqueName(endpoint, usedNames),
                    method(endpoint),
                    url, pairs(endpoint, ParameterLocation.QUERY), pairs(endpoint, ParameterLocation.HEADER),
                    requestAuth(endpoint, folderAuth), body(endpoint, schemas), null,
                    isBlank(endpoint.getId()) ? null : endpoint.getId()));
        }
        if (skippedCookies > 0) {
            warnings.add(name + ": " + skippedCookies + " cookie parameter(s) were not imported");
        }
        AuthConfig auth = folderAuth == null ? null : placeholder(folderAuth);
        return new Folder(null, name, auth, null, new ArrayList<>(variables.values()), null, items);
    }

    private static String unusable(Endpoint endpoint) {
        if (endpoint == null) {
            return "empty endpoint";
        }
        if (isBlank(endpoint.getPath())) {
            return "no path";
        }
        if (isBlank(endpoint.getMethod())) {
            return "no method";
        }
        try {
            method(endpoint);
        } catch (IllegalArgumentException error) {
            return "unsupported method " + endpoint.getMethod();
        }
        return null;
    }

    private static boolean isAnyMethod(Endpoint endpoint) {
        return Endpoint.ANY_METHOD.equalsIgnoreCase(endpoint.getMethod());
    }

    private static HttpMethod method(Endpoint endpoint) {
        return isAnyMethod(endpoint) ? HttpMethod.GET
                : HttpMethod.valueOf(endpoint.getMethod().toUpperCase(Locale.ROOT));
    }

    private static String label(Endpoint endpoint) {
        if (endpoint == null) {
            return "an endpoint";
        }
        if (!isBlank(endpoint.getId())) {
            return endpoint.getId();
        }
        return (endpoint.getMethod() == null ? "?" : endpoint.getMethod()) + " "
                + (endpoint.getPath() == null ? "?" : endpoint.getPath());
    }

    private static String fallbackGroup(Application application) {
        return application == null || isBlank(application.getName()) ? DEFAULT_GROUP : application.getName();
    }

    private static String prefix(Application application) {
        if (application == null) {
            return "";
        }
        return slashed(application.getContextPath()) + slashed(application.getServletPath());
    }

    private static String slashed(String value) {
        if (isBlank(value)) {
            return "";
        }
        String trimmed = value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        if (trimmed.isEmpty()) {
            return "";
        }
        return trimmed.startsWith("/") ? trimmed : "/" + trimmed;
    }

    private static String url(String path, Map<String, Variable> variables, List<String> warnings, Endpoint endpoint) {
        String rooted = path.startsWith("/") ? path : "/" + path;
        Matcher matcher = PLACEHOLDER.matcher(rooted);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String name = matcher.group(1);
            if (VARIABLE_NAME.matcher(name).matches()) {
                variables.computeIfAbsent(name, key -> Variable.of(key, ""));
                matcher.appendReplacement(result, Matcher.quoteReplacement("${" + name + "}"));
            } else {
                warnings.add(label(endpoint) + ": path parameter \"" + name + "\" is not a valid variable name");
                matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group()));
            }
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String uniqueName(Endpoint endpoint, Set<String> used) {
        String method = endpoint.getMethod().toUpperCase(Locale.ROOT);
        String base = isBlank(endpoint.getName()) ? method + " " + endpoint.getPath() : endpoint.getName();
        String candidate = base;
        if (!used.add(candidate)) {
            candidate = base + " " + method;
            int number = 2;
            while (!used.add(candidate)) {
                candidate = base + " " + method + " " + number++;
            }
        }
        return candidate;
    }

    private static List<KeyValue> pairs(Endpoint endpoint, String location) {
        List<KeyValue> result = new ArrayList<>();
        for (Parameter parameter : endpoint.getParameters()) {
            if (location.equals(parameter.getIn()) && !isBlank(parameter.getName())) {
                result.add(new KeyValue(parameter.getName(), valueOf(parameter), parameter.isRequired()));
            }
        }
        return result;
    }

    private static String valueOf(Parameter parameter) {
        return parameter.getDefaultValue() == null ? "" : parameter.getDefaultValue();
    }

    private static AuthType folderAuth(String group, List<Endpoint> endpoints, List<String> warnings) {
        Map<AuthType, Integer> counts = new LinkedHashMap<>();
        Set<String> unknown = new LinkedHashSet<>();
        for (Endpoint endpoint : endpoints) {
            List<String> schemes = declaredSchemes(endpoint.getSecurity());
            if (isOpen(endpoint.getSecurity()) || schemes.isEmpty()) {
                continue;
            }
            AuthType type = authType(schemes.get(0));
            if (type == null) {
                unknown.add(schemes.get(0));
            } else {
                counts.merge(type, 1, Integer::sum);
            }
        }
        if (!unknown.isEmpty()) {
            warnings.add(group + ": unknown security scheme " + String.join(", ", unknown) + ", authorization left empty");
        }
        AuthType best = null;
        for (Map.Entry<AuthType, Integer> entry : counts.entrySet()) {
            if (best == null || entry.getValue() > counts.get(best)) {
                best = entry.getKey();
            }
        }
        if (counts.size() > 1) {
            warnings.add(group + ": endpoints use different security schemes, the folder uses " + best);
        }
        return best;
    }

    private static AuthType authType(String scheme) {
        if (scheme == null) {
            return null;
        }
        String key = scheme.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
        return switch (key) {
            case "bearer", "jwt", "oauth" -> AuthType.BEARER;
            case "basic" -> AuthType.BASIC;
            case "apikey" -> AuthType.API_KEY;
            default -> null;
        };
    }

    private static AuthConfig requestAuth(Endpoint endpoint, AuthType folderAuth) {
        if (isOpen(endpoint.getSecurity())) {
            return folderAuth == null ? null : AuthConfig.none();
        }
        List<String> schemes = declaredSchemes(endpoint.getSecurity());
        AuthType own = schemes.isEmpty() ? null : authType(schemes.get(0));
        return own != null && own != folderAuth ? placeholder(own) : null;
    }

    private static List<String> declaredSchemes(Security security) {
        Authentication authentication = security == null ? null : security.getAuthentication();
        if (authentication == null || !SecurityStatus.DECLARED.equals(authentication.getStatus())) {
            return List.of();
        }
        return authentication.getSchemes();
    }

    private static boolean isOpen(Security security) {
        Authorization authorization = security == null ? null : security.getAuthorization();
        return authorization != null && SecurityStatus.DECLARED.equals(authorization.getStatus())
                && AccessLevel.OPEN.equals(authorization.getAccess());
    }

    private static AuthConfig placeholder(AuthType type) {
        return switch (type) {
            case BEARER -> AuthConfig.bearer("");
            case BASIC -> AuthConfig.basic("", "");
            case API_KEY -> AuthConfig.apiKey("", "");
            default -> AuthConfig.none();
        };
    }

    private static RequestBody body(Endpoint endpoint, Map<String, Schema> schemas) {
        String contentType = contentType(endpoint);
        List<Parameter> formFields = fields(endpoint, ParameterLocation.FORM);
        List<Parameter> partFields = fields(endpoint, ParameterLocation.PART);
        Schema schema = endpoint.getBody() == null ? null : SchemaSample.resolve(endpoint.getBody().getSchema(), schemas);
        if (!partFields.isEmpty() || contentType.startsWith("multipart/")) {
            return new RequestBody(BodyType.MULTIPART, null, null, null, null, parts(partFields, schema, schemas), null);
        }
        if (!formFields.isEmpty() || contentType.equals("application/x-www-form-urlencoded")) {
            return new RequestBody(BodyType.FORM, null, null, null, form(formFields, schema), null, null);
        }
        if (endpoint.getBody() == null) {
            return null;
        }
        BodyType type = bodyType(contentType);
        String text = type == BodyType.JSON ? SchemaSample.json(endpoint.getBody().getSchema(), schemas) : "";
        String declared = contentType.isEmpty() || contentType.equals(type.defaultContentType()) ? null : contentType;
        return new RequestBody(type, text, declared, null, null, null, null);
    }

    private static String contentType(Endpoint endpoint) {
        String declared = endpoint.getBody() != null && !isBlank(endpoint.getBody().getContentType())
                ? endpoint.getBody().getContentType()
                : endpoint.getConsumes().isEmpty() ? "" : endpoint.getConsumes().get(0);
        if (declared == null) {
            return "";
        }
        int semicolon = declared.indexOf(';');
        String bare = semicolon < 0 ? declared : declared.substring(0, semicolon);
        return bare.trim().toLowerCase(Locale.ROOT);
    }

    private static BodyType bodyType(String contentType) {
        if (contentType.isEmpty() || contentType.contains("json")) {
            return BodyType.JSON;
        }
        if (contentType.contains("xml")) {
            return BodyType.XML;
        }
        if (contentType.equals("text/html")) {
            return BodyType.HTML;
        }
        if (contentType.equals("application/octet-stream")) {
            return BodyType.BINARY;
        }
        return BodyType.TEXT;
    }

    private static List<Parameter> fields(Endpoint endpoint, String location) {
        List<Parameter> result = new ArrayList<>();
        for (Parameter parameter : endpoint.getParameters()) {
            if (location.equals(parameter.getIn()) && !isBlank(parameter.getName())) {
                result.add(parameter);
            }
        }
        return result;
    }

    private static List<KeyValue> form(List<Parameter> fields, Schema schema) {
        List<KeyValue> result = new ArrayList<>();
        for (Parameter field : fields) {
            result.add(new KeyValue(field.getName(), valueOf(field), field.isRequired()));
        }
        if (result.isEmpty() && schema != null) {
            for (Map.Entry<String, Schema> property : schema.getProperties().entrySet()) {
                result.add(new KeyValue(property.getKey(), "", schema.getRequired().contains(property.getKey())));
            }
        }
        return result;
    }

    private static List<FormPart> parts(List<Parameter> fields, Schema schema, Map<String, Schema> schemas) {
        List<FormPart> result = new ArrayList<>();
        for (Parameter field : fields) {
            result.add(isFile(field.getSchema(), schemas) ? new FormPart(field.getName(), PartKind.FILE, "", "", field.isRequired())
                    : new FormPart(field.getName(), PartKind.TEXT, valueOf(field), "", field.isRequired()));
        }
        if (result.isEmpty() && schema != null) {
            for (Map.Entry<String, Schema> property : schema.getProperties().entrySet()) {
                boolean required = schema.getRequired().contains(property.getKey());
                result.add(isFile(property.getValue(), schemas)
                        ? new FormPart(property.getKey(), PartKind.FILE, "", "", required)
                        : new FormPart(property.getKey(), PartKind.TEXT, "", "", required));
            }
        }
        return result;
    }

    private static boolean isFile(Schema schema, Map<String, Schema> schemas) {
        Schema resolved = SchemaSample.resolve(schema, schemas);
        return resolved != null && SchemaType.FILE.equals(resolved.getType());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
