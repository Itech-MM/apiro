package io.github.itech_mm.apiro.spec;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class CanonicalTree {
    private CanonicalTree() {
    }

    static String drift(String json) {
        Object file = MiniJson.parse(json);
        Manifest manifest = ManifestJsonReader.read(json);
        return firstDifference("$", withoutGeneratedAt(file), withoutGeneratedAt(of(manifest)));
    }

    static Map<String, Object> of(Manifest manifest) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("manifestVersion", whole(manifest.getManifestVersion()));
        put(root, "source", manifest.getSource());
        put(root, "generator", generator(manifest.getGenerator()));
        put(root, "generatedAt", manifest.getGeneratedAt());
        put(root, "application", application(manifest.getApplication()));
        List<Object> endpoints = new ArrayList<>();
        for (Endpoint endpoint : manifest.getEndpoints()) {
            endpoints.add(endpoint(endpoint));
        }
        root.put("endpoints", endpoints);
        put(root, "schemas", schemaMap(manifest.getSchemas()));
        return root;
    }

    @SuppressWarnings("unchecked")
    private static Object withoutGeneratedAt(Object tree) {
        if (tree instanceof Map) {
            ((Map<String, Object>) tree).remove("generatedAt");
        }
        return tree;
    }

    @SuppressWarnings("unchecked")
    private static String firstDifference(String path, Object file, Object canonical) {
        if (file instanceof Map && canonical instanceof Map) {
            Map<String, Object> fileMap = (Map<String, Object>) file;
            Map<String, Object> canonicalMap = (Map<String, Object>) canonical;
            List<String> fileKeys = new ArrayList<>(fileMap.keySet());
            List<String> canonicalKeys = new ArrayList<>(canonicalMap.keySet());
            if (!fileKeys.equals(canonicalKeys)) {
                return path + ": the file has fields " + fileKeys + " but the canonical form has " + canonicalKeys;
            }
            for (String key : fileKeys) {
                String difference = firstDifference(path + "." + key, fileMap.get(key), canonicalMap.get(key));
                if (difference != null) {
                    return difference;
                }
            }
            return null;
        }
        if (file instanceof List && canonical instanceof List) {
            List<Object> fileList = (List<Object>) file;
            List<Object> canonicalList = (List<Object>) canonical;
            if (fileList.size() != canonicalList.size()) {
                return path + ": the file has " + fileList.size() + " items but the canonical form has "
                        + canonicalList.size();
            }
            for (int i = 0; i < fileList.size(); i++) {
                String difference = firstDifference(path + "[" + i + "]", fileList.get(i), canonicalList.get(i));
                if (difference != null) {
                    return difference;
                }
            }
            return null;
        }
        if (Objects.equals(file, canonical)) {
            return null;
        }
        return path + ": the file has " + file + " but the canonical form has " + canonical;
    }

    private static void put(Map<String, Object> map, String key, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof String && ((String) value).isEmpty()) {
            return;
        }
        if (value instanceof Boolean && !((Boolean) value)) {
            return;
        }
        if (value instanceof Collection && ((Collection<?>) value).isEmpty()) {
            return;
        }
        if (value instanceof Map && ((Map<?, ?>) value).isEmpty()) {
            return;
        }
        map.put(key, value);
    }

    private static MiniJson.Num whole(int value) {
        return new MiniJson.Num(Integer.toString(value));
    }

    private static MiniJson.Num decimal(double value) {
        if (value == Math.rint(value) && Math.abs(value) < 1e15) {
            return new MiniJson.Num(Long.toString((long) value));
        }
        return new MiniJson.Num(BigDecimal.valueOf(value).toPlainString());
    }

    private static Map<String, Object> generator(Generator generator) {
        if (generator == null) {
            return null;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        put(map, "name", generator.getName());
        put(map, "version", generator.getVersion());
        put(map, "framework", generator.getFramework());
        put(map, "frameworkVersion", generator.getFrameworkVersion());
        return map;
    }

    private static Map<String, Object> application(Application application) {
        if (application == null) {
            return null;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        put(map, "name", application.getName());
        put(map, "contextPath", application.getContextPath());
        put(map, "servletPath", application.getServletPath());
        return map;
    }

    private static Map<String, Object> endpoint(Endpoint endpoint) {
        Map<String, Object> map = new LinkedHashMap<>();
        put(map, "id", endpoint.getId());
        put(map, "method", endpoint.getMethod());
        put(map, "path", endpoint.getPath());
        put(map, "pattern", endpoint.getPattern());
        put(map, "group", endpoint.getGroup());
        put(map, "name", endpoint.getName());
        put(map, "summary", endpoint.getSummary());
        put(map, "deprecated", endpoint.isDeprecated());
        put(map, "consumes", endpoint.getConsumes());
        put(map, "produces", endpoint.getProduces());
        put(map, "conditions", conditions(endpoint.getConditions()));
        List<Object> parameters = new ArrayList<>();
        for (Parameter parameter : endpoint.getParameters()) {
            parameters.add(parameter(parameter));
        }
        put(map, "parameters", parameters);
        put(map, "body", body(endpoint.getBody()));
        put(map, "security", security(endpoint.getSecurity()));
        put(map, "handler", handler(endpoint.getHandler()));
        return map;
    }

    private static Map<String, Object> parameter(Parameter parameter) {
        Map<String, Object> map = new LinkedHashMap<>();
        put(map, "name", parameter.getName());
        put(map, "in", parameter.getIn());
        put(map, "required", parameter.isRequired());
        put(map, "defaultValue", parameter.getDefaultValue());
        put(map, "description", parameter.getDescription());
        put(map, "schema", parameter.getSchema() == null ? null : schema(parameter.getSchema()));
        return map;
    }

    private static Map<String, Object> body(Body body) {
        if (body == null) {
            return null;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        put(map, "contentType", body.getContentType());
        put(map, "required", body.isRequired());
        put(map, "schema", body.getSchema() == null ? null : schema(body.getSchema()));
        return map;
    }

    private static Map<String, Object> conditions(Conditions conditions) {
        if (conditions == null) {
            return null;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        put(map, "params", conditions.getParams());
        put(map, "headers", conditions.getHeaders());
        put(map, "unsupported", conditions.getUnsupported());
        return map;
    }

    private static Map<String, Object> security(Security security) {
        if (security == null) {
            return null;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        put(map, "authentication", authentication(security.getAuthentication()));
        put(map, "authorization", authorization(security.getAuthorization()));
        return map;
    }

    private static Map<String, Object> authentication(Authentication authentication) {
        if (authentication == null) {
            return null;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        put(map, "status", authentication.getStatus());
        put(map, "schemes", authentication.getSchemes());
        put(map, "source", authentication.getSource());
        return map;
    }

    private static Map<String, Object> authorization(Authorization authorization) {
        if (authorization == null) {
            return null;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        put(map, "status", authorization.getStatus());
        put(map, "access", authorization.getAccess());
        put(map, "source", authorization.getSource());
        return map;
    }

    private static Map<String, Object> handler(Handler handler) {
        if (handler == null) {
            return null;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        put(map, "className", handler.getClassName());
        put(map, "methodName", handler.getMethodName());
        put(map, "parameterTypes", handler.getParameterTypes());
        return map;
    }

    private static Map<String, Object> schemaMap(Map<String, Schema> schemas) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (Map.Entry<String, Schema> entry : schemas.entrySet()) {
            map.put(entry.getKey(), schema(entry.getValue()));
        }
        return map;
    }

    private static Map<String, Object> schema(Schema schema) {
        Map<String, Object> map = new LinkedHashMap<>();
        put(map, "type", schema.getType());
        put(map, "title", schema.getTitle());
        put(map, "format", schema.getFormat());
        put(map, "ref", schema.getRef());
        put(map, "items", schema.getItems() == null ? null : schema(schema.getItems()));
        put(map, "properties", schemaMap(schema.getProperties()));
        put(map, "required", schema.getRequired());
        put(map, "enumValues", schema.getEnumValues());
        put(map, "nullable", schema.getNullable());
        put(map, "minimum", schema.getMinimum() == null ? null : decimal(schema.getMinimum()));
        put(map, "maximum", schema.getMaximum() == null ? null : decimal(schema.getMaximum()));
        put(map, "minLength", schema.getMinLength() == null ? null : whole(schema.getMinLength()));
        put(map, "maxLength", schema.getMaxLength() == null ? null : whole(schema.getMaxLength()));
        put(map, "pattern", schema.getPattern());
        put(map, "minItems", schema.getMinItems() == null ? null : whole(schema.getMinItems()));
        put(map, "maxItems", schema.getMaxItems() == null ? null : whole(schema.getMaxItems()));
        put(map, "example", schema.getExample());
        return map;
    }
}
