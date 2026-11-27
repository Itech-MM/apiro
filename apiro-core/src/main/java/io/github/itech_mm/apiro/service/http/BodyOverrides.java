package io.github.itech_mm.apiro.service.http;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import io.github.itech_mm.apiro.model.BodyOverride;
import io.github.itech_mm.apiro.model.BodyType;
import io.github.itech_mm.apiro.model.FieldOverride;
import io.github.itech_mm.apiro.model.RequestBody;
import io.github.itech_mm.apiro.model.RequestItem;
import io.github.itech_mm.apiro.service.scenario.JsonPath;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BodyOverrides {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeNulls().disableHtmlEscaping().create();
    private static final String SENTINEL = "__apiro_ref_";

    private BodyOverrides() {
    }

    public static RequestItem apply(RequestItem request, BodyOverride override) {
        if (override == null) {
            return request;
        }
        RequestBody body = request.body();
        RequestBody next = switch (override.mode()) {
            case REPLACE -> replaced(body, override.text());
            case PATCH -> override.fields().stream().noneMatch(FieldOverride::enabled) ? body
                    : withText(body, patch(body, override.fields()));
        };
        return new RequestItem(request.id(), request.name(), request.method(), request.url(), request.query(),
                request.headers(), request.auth(), next, request.settings(), request.manifestEndpointId());
    }

    public static String patch(RequestBody body, List<FieldOverride> fields) {
        if (body.type() != BodyType.JSON && body.type() != BodyType.NONE) {
            throw new IllegalArgumentException("Body override: the request body is " + body.type()
                    + ", patching needs JSON. Use Replace instead");
        }
        return patchText(body.text(), fields);
    }

    public static String patchText(String text, List<FieldOverride> fields) {
        Masker masker = new Masker();
        String masked = masker.maskBare(text);
        JsonElement root;
        try {
            root = masked.isBlank() ? new JsonObject() : JsonParser.parseString(masked);
        } catch (JsonParseException error) {
            throw new IllegalArgumentException("Body override: the request body is not valid JSON, use Replace instead");
        }
        if (!root.isJsonObject() && !root.isJsonArray()) {
            throw new IllegalArgumentException("Body override: the request body is not a JSON object or array");
        }
        for (FieldOverride field : fields) {
            if (field.enabled() && !field.path().isBlank()) {
                root = set(root, field, masker);
            }
        }
        return masker.restore(GSON.toJson(root));
    }

    private static RequestBody replaced(RequestBody body, String text) {
        BodyType type = body.type().isText() ? body.type() : BodyType.JSON;
        return new RequestBody(type, text, body.contentType(), body.charset(), body.form(), body.parts(),
                body.filePath());
    }

    private static RequestBody withText(RequestBody body, String text) {
        BodyType type = body.type() == BodyType.NONE ? BodyType.JSON : body.type();
        return new RequestBody(type, text, body.contentType(), body.charset(), body.form(), body.parts(),
                body.filePath());
    }

    private static JsonElement set(JsonElement root, FieldOverride field, Masker masker) {
        List<Object> segments;
        try {
            segments = JsonPath.compile(field.path()).segments();
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Body override: " + error.getMessage());
        }
        if (segments.isEmpty()) {
            throw new IllegalArgumentException("Body override: path " + field.path() + " has no field");
        }
        JsonElement parent = root;
        for (int i = 0; i < segments.size() - 1; i++) {
            Object segment = segments.get(i);
            Object following = segments.get(i + 1);
            JsonElement child = child(parent, segment, field.path());
            if (child == null) {
                if (field.type() == FieldOverride.ValueType.REMOVE) {
                    return root;
                }
                if (!(segment instanceof String key) || !parent.isJsonObject()) {
                    throw missing(field.path());
                }
                child = following instanceof Integer ? new JsonArray() : new JsonObject();
                parent.getAsJsonObject().add(key, child);
            }
            parent = child;
        }
        Object last = segments.get(segments.size() - 1);
        if (field.type() == FieldOverride.ValueType.REMOVE) {
            remove(parent, last);
            return root;
        }
        assign(parent, last, value(field, masker), field.path());
        return root;
    }

    private static JsonElement child(JsonElement parent, Object segment, String path) {
        if (segment instanceof String key) {
            if (!parent.isJsonObject()) {
                throw new IllegalArgumentException("Body override: " + path + " goes through a value that is not an object");
            }
            JsonElement found = parent.getAsJsonObject().get(key);
            return found == null || found.isJsonNull() ? null : found;
        }
        int index = (Integer) segment;
        if (!parent.isJsonArray()) {
            throw new IllegalArgumentException("Body override: " + path + " uses an index on a value that is not an array");
        }
        JsonArray array = parent.getAsJsonArray();
        if (index >= array.size()) {
            throw missing(path);
        }
        return array.get(index);
    }

    private static void remove(JsonElement parent, Object last) {
        if (last instanceof String key && parent.isJsonObject()) {
            parent.getAsJsonObject().remove(key);
        } else if (last instanceof Integer index && parent.isJsonArray() && index < parent.getAsJsonArray().size()) {
            parent.getAsJsonArray().remove(index.intValue());
        }
    }

    private static void assign(JsonElement parent, Object last, JsonElement value, String path) {
        if (last instanceof String key) {
            if (!parent.isJsonObject()) {
                throw new IllegalArgumentException("Body override: " + path + " sets a field on a value that is not an object");
            }
            parent.getAsJsonObject().add(key, value);
            return;
        }
        int index = (Integer) last;
        if (!parent.isJsonArray()) {
            throw new IllegalArgumentException("Body override: " + path + " uses an index on a value that is not an array");
        }
        JsonArray array = parent.getAsJsonArray();
        if (index < array.size()) {
            array.set(index, value);
        } else if (index == array.size()) {
            array.add(value);
        } else {
            throw missing(path);
        }
    }

    private static IllegalArgumentException missing(String path) {
        return new IllegalArgumentException("Body override: " + path + " was not found in the body");
    }

    private static JsonElement value(FieldOverride field, Masker masker) {
        String text = field.value();
        boolean reference = text.contains("${");
        switch (field.type()) {
            case STRING:
                return new JsonPrimitive(text);
            case NULL:
                return JsonNull.INSTANCE;
            case NUMBER:
                if (reference) {
                    return masker.token(text.trim());
                }
                try {
                    return new JsonPrimitive(new BigDecimal(text.trim()));
                } catch (NumberFormatException error) {
                    throw new IllegalArgumentException("Body override: " + field.path() + " value '" + text
                            + "' is not a number");
                }
            case BOOLEAN:
                if (reference) {
                    return masker.token(text.trim());
                }
                if (!text.trim().equals("true") && !text.trim().equals("false")) {
                    throw new IllegalArgumentException("Body override: " + field.path() + " value '" + text
                            + "' is not true or false");
                }
                return new JsonPrimitive(Boolean.parseBoolean(text.trim()));
            case JSON:
                try {
                    return JsonParser.parseString(masker.maskBare(text));
                } catch (JsonParseException error) {
                    throw new IllegalArgumentException("Body override: " + field.path() + " value is not valid JSON");
                }
            default:
                throw new IllegalArgumentException("Body override: unsupported type " + field.type());
        }
    }

    private static final class Masker {

        private final Map<String, String> tokens = new LinkedHashMap<>();

        JsonPrimitive token(String original) {
            String key = SENTINEL + tokens.size() + "__";
            tokens.put(key, original);
            return new JsonPrimitive(key);
        }

        String maskBare(String text) {
            StringBuilder out = new StringBuilder(text.length());
            boolean inString = false;
            boolean escaped = false;
            int i = 0;
            while (i < text.length()) {
                char c = text.charAt(i);
                if (inString) {
                    out.append(c);
                    if (escaped) {
                        escaped = false;
                    } else if (c == '\\') {
                        escaped = true;
                    } else if (c == '"') {
                        inString = false;
                    }
                    i++;
                } else if (c == '"') {
                    inString = true;
                    out.append(c);
                    i++;
                } else if (c == '$' && i + 1 < text.length() && text.charAt(i + 1) == '{') {
                    int end = text.indexOf('}', i);
                    if (end < 0) {
                        out.append(c);
                        i++;
                    } else {
                        String key = SENTINEL + tokens.size() + "__";
                        tokens.put(key, text.substring(i, end + 1));
                        out.append('"').append(key).append('"');
                        i = end + 1;
                    }
                } else {
                    out.append(c);
                    i++;
                }
            }
            return out.toString();
        }

        String restore(String json) {
            String result = json;
            List<String> keys = new ArrayList<>(tokens.keySet());
            for (String key : keys) {
                result = result.replace("\"" + key + "\"", tokens.get(key));
            }
            return result;
        }
    }
}
