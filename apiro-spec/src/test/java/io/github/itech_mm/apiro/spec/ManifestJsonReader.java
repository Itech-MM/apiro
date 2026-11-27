package io.github.itech_mm.apiro.spec;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

final class ManifestJsonReader {
    private static final Pattern WHOLE = Pattern.compile("-?[0-9]+");

    private ManifestJsonReader() {
    }

    static Manifest read(String json) {
        return manifest(MiniJson.parse(json), "$");
    }

    private static IllegalArgumentException fail(String path, String message) {
        return new IllegalArgumentException(path + " " + message);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> anyObject(Object value, String path) {
        if (!(value instanceof Map)) {
            throw fail(path, "must be an object");
        }
        return (Map<String, Object>) value;
    }

    private static Map<String, Object> object(Object value, String path, String... allowed) {
        Map<String, Object> map = anyObject(value, path);
        List<String> names = Arrays.asList(allowed);
        for (String key : map.keySet()) {
            if (!names.contains(key)) {
                throw fail(path, "has unknown field '" + key + "'");
            }
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Object value, String path) {
        if (!(value instanceof List)) {
            throw fail(path, "must be an array");
        }
        return (List<Object>) value;
    }

    private static String text(Map<String, Object> map, String key, String path) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        if (!(value instanceof String)) {
            throw fail(path + "." + key, "must be a string");
        }
        return (String) value;
    }

    private static Boolean flag(Map<String, Object> map, String key, String path) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        if (!(value instanceof Boolean)) {
            throw fail(path + "." + key, "must be true or false");
        }
        return (Boolean) value;
    }

    private static Integer whole(Map<String, Object> map, String key, String path) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        if (!(value instanceof MiniJson.Num) || !WHOLE.matcher(((MiniJson.Num) value).literal()).matches()) {
            throw fail(path + "." + key, "must be a whole number written without a decimal point");
        }
        try {
            return Integer.valueOf(((MiniJson.Num) value).literal());
        } catch (NumberFormatException e) {
            throw fail(path + "." + key, "is out of range");
        }
    }

    private static Double decimal(Map<String, Object> map, String key, String path) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        if (!(value instanceof MiniJson.Num)) {
            throw fail(path + "." + key, "must be a number");
        }
        return Double.valueOf(((MiniJson.Num) value).literal());
    }

    private static List<String> texts(Map<String, Object> map, String key, String path) {
        Object value = map.get(key);
        List<String> result = new ArrayList<>();
        if (value == null) {
            return result;
        }
        List<Object> items = list(value, path + "." + key);
        for (int i = 0; i < items.size(); i++) {
            if (!(items.get(i) instanceof String)) {
                throw fail(path + "." + key + "[" + i + "]", "must be a string");
            }
            result.add((String) items.get(i));
        }
        return result;
    }

    private static Manifest manifest(Object value, String path) {
        Map<String, Object> map = object(value, path, "manifestVersion", "source", "generator", "generatedAt",
                "application", "endpoints", "schemas");
        Manifest manifest = new Manifest();
        Integer version = whole(map, "manifestVersion", path);
        if (version != null) {
            manifest.setManifestVersion(version);
        }
        manifest.setSource(text(map, "source", path));
        if (map.containsKey("generator")) {
            manifest.setGenerator(generator(map.get("generator"), path + ".generator"));
        }
        manifest.setGeneratedAt(text(map, "generatedAt", path));
        if (map.containsKey("application")) {
            manifest.setApplication(application(map.get("application"), path + ".application"));
        }
        if (map.containsKey("endpoints")) {
            List<Object> items = list(map.get("endpoints"), path + ".endpoints");
            List<Endpoint> endpoints = new ArrayList<>();
            for (int i = 0; i < items.size(); i++) {
                endpoints.add(endpoint(items.get(i), path + ".endpoints[" + i + "]"));
            }
            manifest.setEndpoints(endpoints);
        }
        if (map.containsKey("schemas")) {
            manifest.setSchemas(schemaMap(map.get("schemas"), path + ".schemas"));
        }
        return manifest;
    }

    private static Generator generator(Object value, String path) {
        Map<String, Object> map = object(value, path, "name", "version", "framework", "frameworkVersion");
        return new Generator()
                .setName(text(map, "name", path))
                .setVersion(text(map, "version", path))
                .setFramework(text(map, "framework", path))
                .setFrameworkVersion(text(map, "frameworkVersion", path));
    }

    private static Application application(Object value, String path) {
        Map<String, Object> map = object(value, path, "name", "contextPath", "servletPath");
        return new Application()
                .setName(text(map, "name", path))
                .setContextPath(text(map, "contextPath", path))
                .setServletPath(text(map, "servletPath", path));
    }

    private static Endpoint endpoint(Object value, String path) {
        Map<String, Object> map = object(value, path, "id", "method", "path", "pattern", "group", "name", "summary",
                "deprecated", "consumes", "produces", "conditions",
                "parameters", "body", "security", "handler");
        Endpoint endpoint = new Endpoint()
                .setId(text(map, "id", path))
                .setMethod(text(map, "method", path))
                .setPath(text(map, "path", path))
                .setPattern(text(map, "pattern", path))
                .setGroup(text(map, "group", path))
                .setName(text(map, "name", path))
                .setSummary(text(map, "summary", path))
                .setConsumes(texts(map, "consumes", path))
                .setProduces(texts(map, "produces", path));
        Boolean deprecated = flag(map, "deprecated", path);
        if (deprecated != null) {
            endpoint.setDeprecated(deprecated);
        }
        if (map.containsKey("conditions")) {
            endpoint.setConditions(conditions(map.get("conditions"), path + ".conditions"));
        }
        if (map.containsKey("parameters")) {
            List<Object> items = list(map.get("parameters"), path + ".parameters");
            List<Parameter> parameters = new ArrayList<>();
            for (int i = 0; i < items.size(); i++) {
                parameters.add(parameter(items.get(i), path + ".parameters[" + i + "]"));
            }
            endpoint.setParameters(parameters);
        }
        if (map.containsKey("body")) {
            endpoint.setBody(body(map.get("body"), path + ".body"));
        }
        if (map.containsKey("security")) {
            endpoint.setSecurity(security(map.get("security"), path + ".security"));
        }
        if (map.containsKey("handler")) {
            endpoint.setHandler(handler(map.get("handler"), path + ".handler"));
        }
        return endpoint;
    }

    private static Parameter parameter(Object value, String path) {
        Map<String, Object> map = object(value, path, "name", "in", "required", "defaultValue", "schema",
                "description");
        Parameter parameter = new Parameter()
                .setName(text(map, "name", path))
                .setIn(text(map, "in", path))
                .setDefaultValue(text(map, "defaultValue", path))
                .setDescription(text(map, "description", path));
        Boolean required = flag(map, "required", path);
        if (required != null) {
            parameter.setRequired(required);
        }
        if (map.containsKey("schema")) {
            parameter.setSchema(schema(map.get("schema"), path + ".schema"));
        }
        return parameter;
    }

    private static Body body(Object value, String path) {
        Map<String, Object> map = object(value, path, "contentType", "required", "schema");
        Body body = new Body().setContentType(text(map, "contentType", path));
        Boolean required = flag(map, "required", path);
        if (required != null) {
            body.setRequired(required);
        }
        if (map.containsKey("schema")) {
            body.setSchema(schema(map.get("schema"), path + ".schema"));
        }
        return body;
    }

    private static Conditions conditions(Object value, String path) {
        Map<String, Object> map = object(value, path, "params", "headers", "unsupported");
        return new Conditions()
                .setParams(texts(map, "params", path))
                .setHeaders(texts(map, "headers", path))
                .setUnsupported(texts(map, "unsupported", path));
    }

    private static Security security(Object value, String path) {
        Map<String, Object> map = object(value, path, "authentication", "authorization");
        Security security = new Security();
        if (map.containsKey("authentication")) {
            security.setAuthentication(authentication(map.get("authentication"), path + ".authentication"));
        }
        if (map.containsKey("authorization")) {
            security.setAuthorization(authorization(map.get("authorization"), path + ".authorization"));
        }
        return security;
    }

    private static Authentication authentication(Object value, String path) {
        Map<String, Object> map = object(value, path, "status", "schemes", "source");
        return new Authentication()
                .setStatus(text(map, "status", path))
                .setSchemes(texts(map, "schemes", path))
                .setSource(text(map, "source", path));
    }

    private static Authorization authorization(Object value, String path) {
        Map<String, Object> map = object(value, path, "status", "access", "source");
        return new Authorization()
                .setStatus(text(map, "status", path))
                .setAccess(text(map, "access", path))
                .setSource(text(map, "source", path));
    }

    private static Handler handler(Object value, String path) {
        Map<String, Object> map = object(value, path, "className", "methodName", "parameterTypes");
        return new Handler()
                .setClassName(text(map, "className", path))
                .setMethodName(text(map, "methodName", path))
                .setParameterTypes(texts(map, "parameterTypes", path));
    }

    private static Map<String, Schema> schemaMap(Object value, String path) {
        Map<String, Object> map = anyObject(value, path);
        Map<String, Schema> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            result.put(entry.getKey(), schema(entry.getValue(), path + "." + entry.getKey()));
        }
        return result;
    }

    private static Schema schema(Object value, String path) {
        Map<String, Object> map = object(value, path, "type", "title", "format", "ref", "items", "properties", "required",
                "enumValues", "nullable", "minimum", "maximum", "minLength", "maxLength", "pattern", "minItems",
                "maxItems", "example");
        Schema schema = new Schema()
                .setType(text(map, "type", path))
                .setTitle(text(map, "title", path))
                .setFormat(text(map, "format", path))
                .setRef(text(map, "ref", path))
                .setRequired(texts(map, "required", path))
                .setEnumValues(texts(map, "enumValues", path))
                .setNullable(flag(map, "nullable", path))
                .setMinimum(decimal(map, "minimum", path))
                .setMaximum(decimal(map, "maximum", path))
                .setMinLength(whole(map, "minLength", path))
                .setMaxLength(whole(map, "maxLength", path))
                .setPattern(text(map, "pattern", path))
                .setMinItems(whole(map, "minItems", path))
                .setMaxItems(whole(map, "maxItems", path))
                .setExample(text(map, "example", path));
        if (map.containsKey("items")) {
            schema.setItems(schema(map.get("items"), path + ".items"));
        }
        if (map.containsKey("properties")) {
            schema.setProperties(schemaMap(map.get("properties"), path + ".properties"));
        }
        return schema;
    }
}
