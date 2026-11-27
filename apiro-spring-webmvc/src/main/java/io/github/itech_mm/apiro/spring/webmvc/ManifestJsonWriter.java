package io.github.itech_mm.apiro.spring.webmvc;

import io.github.itech_mm.apiro.spec.Application;
import io.github.itech_mm.apiro.spec.Authentication;
import io.github.itech_mm.apiro.spec.Authorization;
import io.github.itech_mm.apiro.spec.Body;
import io.github.itech_mm.apiro.spec.Conditions;
import io.github.itech_mm.apiro.spec.Endpoint;
import io.github.itech_mm.apiro.spec.Generator;
import io.github.itech_mm.apiro.spec.Handler;
import io.github.itech_mm.apiro.spec.Manifest;
import io.github.itech_mm.apiro.spec.Parameter;
import io.github.itech_mm.apiro.spec.Schema;
import io.github.itech_mm.apiro.spec.Security;
import io.github.itech_mm.apiro.spec.SecurityStatus;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class ManifestJsonWriter {
    private ManifestJsonWriter() {
    }

    public static String write(Manifest manifest) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("manifestVersion", manifest.getManifestVersion());
        put(root, "source", manifest.getSource());
        put(root, "generator", generator(manifest.getGenerator()));
        put(root, "generatedAt", manifest.getGeneratedAt());
        put(root, "application", application(manifest.getApplication()));
        List<Object> endpoints = new ArrayList<>();
        for (Endpoint endpoint : manifest.getEndpoints()) {
            endpoints.add(endpoint(endpoint));
        }
        root.put("endpoints", endpoints);
        Map<String, Schema> sorted = new TreeMap<>(CodePoints.ORDER);
        sorted.putAll(manifest.getSchemas());
        put(root, "schemas", schemas(sorted));
        StringBuilder out = new StringBuilder();
        value(out, root);
        return out.toString();
    }

    private static void put(Map<String, Object> map, String key, Object value) {
        if (value == null || Boolean.FALSE.equals(value)
                || value instanceof String && ((String) value).isEmpty()
                || value instanceof List && ((List<?>) value).isEmpty()
                || value instanceof Map && ((Map<?, ?>) value).isEmpty()) {
            return;
        }
        map.put(key, value);
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

    private static Map<String, Object> parameter(Parameter parameter) {
        Map<String, Object> map = new LinkedHashMap<>();
        put(map, "name", parameter.getName());
        put(map, "in", parameter.getIn());
        put(map, "required", parameter.isRequired());
        put(map, "defaultValue", parameter.getDefaultValue());
        put(map, "description", parameter.getDescription());
        put(map, "schema", schema(parameter.getSchema()));
        return map;
    }

    private static Map<String, Object> body(Body body) {
        if (body == null) {
            return null;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        put(map, "contentType", body.getContentType());
        put(map, "required", body.isRequired());
        put(map, "schema", schema(body.getSchema()));
        return map;
    }

    private static Map<String, Object> security(Security security) {
        if (security == null) {
            return null;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        Authentication authentication = security.getAuthentication();
        if (authentication != null && SecurityStatus.DECLARED.equals(authentication.getStatus())) {
            Map<String, Object> values = new LinkedHashMap<>();
            put(values, "status", authentication.getStatus());
            put(values, "schemes", authentication.getSchemes());
            put(values, "source", authentication.getSource());
            map.put("authentication", values);
        }
        Authorization authorization = security.getAuthorization();
        if (authorization != null && SecurityStatus.DECLARED.equals(authorization.getStatus())) {
            Map<String, Object> values = new LinkedHashMap<>();
            put(values, "status", authorization.getStatus());
            put(values, "access", authorization.getAccess());
            put(values, "source", authorization.getSource());
            map.put("authorization", values);
        }
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

    private static Map<String, Object> schemas(Map<String, Schema> schemas) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (Map.Entry<String, Schema> entry : schemas.entrySet()) {
            map.put(entry.getKey(), schema(entry.getValue()));
        }
        return map;
    }

    private static Map<String, Object> schema(Schema schema) {
        if (schema == null) {
            return null;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        put(map, "type", schema.getType());
        put(map, "title", schema.getTitle());
        put(map, "format", schema.getFormat());
        put(map, "ref", schema.getRef());
        put(map, "items", schema(schema.getItems()));
        put(map, "properties", schemas(schema.getProperties()));
        put(map, "required", schema.getRequired());
        put(map, "enumValues", schema.getEnumValues());
        put(map, "nullable", schema.getNullable());
        put(map, "minimum", number(schema.getMinimum()));
        put(map, "maximum", number(schema.getMaximum()));
        put(map, "minLength", schema.getMinLength());
        put(map, "maxLength", schema.getMaxLength());
        put(map, "pattern", schema.getPattern());
        put(map, "minItems", schema.getMinItems());
        put(map, "maxItems", schema.getMaxItems());
        put(map, "example", schema.getExample());
        return map;
    }

    private static BigDecimal number(Double value) {
        if (value == null || value.isNaN() || value.isInfinite()) {
            return null;
        }
        return value == 0d ? BigDecimal.ZERO : BigDecimal.valueOf(value).stripTrailingZeros();
    }

    private static void value(StringBuilder out, Object value) {
        if (value instanceof String) {
            string(out, (String) value);
        } else if (value instanceof Map) {
            out.append('{');
            String separator = "";
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                out.append(separator);
                string(out, (String) entry.getKey());
                out.append(':');
                value(out, entry.getValue());
                separator = ",";
            }
            out.append('}');
        } else if (value instanceof List) {
            out.append('[');
            String separator = "";
            for (Object item : (List<?>) value) {
                out.append(separator);
                value(out, item);
                separator = ",";
            }
            out.append(']');
        } else if (value instanceof BigDecimal) {
            out.append(((BigDecimal) value).toPlainString());
        } else {
            out.append(value);
        }
    }

    private static void string(StringBuilder out, String text) {
        out.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"' || c == '\\') {
                out.append('\\').append(c);
            } else if (c < 0x20) {
                out.append(String.format("\\u%04x", (int) c));
            } else {
                out.append(c);
            }
        }
        out.append('"');
    }
}
