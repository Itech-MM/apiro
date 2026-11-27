package io.github.itech_mm.apiro.service.manifest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.github.itech_mm.apiro.spec.Schema;
import io.github.itech_mm.apiro.spec.SchemaType;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class SchemaSample {

    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final int MAX_DEPTH = 5;
    private static final int MAX_PADDING = 64;

    private final Map<String, Schema> named;
    private final Set<String> active = new HashSet<>();

    private SchemaSample(Map<String, Schema> named) {
        this.named = named;
    }

    public static String json(Schema schema, Map<String, Schema> named) {
        JsonElement sample = new SchemaSample(named).build(schema, 0);
        return PRETTY.toJson(sample == null ? JsonNull.INSTANCE : sample);
    }

    public static Schema resolve(Schema schema, Map<String, Schema> named) {
        Schema current = schema;
        for (int hops = 0; current != null && current.getRef() != null && hops < MAX_DEPTH; hops++) {
            current = named.get(current.getRef());
        }
        return current != null && current.getRef() == null ? current : null;
    }

    private JsonElement build(Schema schema, int depth) {
        if (schema == null || depth > MAX_DEPTH) {
            return null;
        }
        if (schema.getRef() != null) {
            String name = schema.getRef();
            Schema target = named.get(name);
            if (target == null || !active.add(name)) {
                return null;
            }
            try {
                return build(target, depth + 1);
            } finally {
                active.remove(name);
            }
        }
        String type = schema.getType() == null ? SchemaType.ANY : schema.getType();
        return switch (type) {
            case SchemaType.OBJECT -> object(schema, depth);
            case SchemaType.ARRAY -> array(schema, depth);
            case SchemaType.INTEGER -> new JsonPrimitive(whole(schema));
            case SchemaType.NUMBER -> number(schema);
            case SchemaType.BOOLEAN -> new JsonPrimitive(false);
            case SchemaType.STRING -> new JsonPrimitive(text(schema));
            case SchemaType.FILE -> new JsonPrimitive("");
            default -> JsonNull.INSTANCE;
        };
    }

    private JsonElement object(Schema schema, int depth) {
        JsonObject result = new JsonObject();
        for (Map.Entry<String, Schema> property : schema.getProperties().entrySet()) {
            JsonElement value = build(property.getValue(), depth + 1);
            if (value != null) {
                result.add(property.getKey(), value);
            }
        }
        return result;
    }

    private JsonElement array(Schema schema, int depth) {
        JsonArray result = new JsonArray();
        JsonElement item = build(schema.getItems(), depth + 1);
        if (item != null) {
            result.add(item);
        }
        return result;
    }

    private static long whole(Schema schema) {
        if (schema.getMinimum() != null && schema.getMinimum() > 0) {
            return (long) Math.ceil(schema.getMinimum());
        }
        if (schema.getMaximum() != null && schema.getMaximum() < 0) {
            return (long) Math.floor(schema.getMaximum());
        }
        return 0;
    }

    private static JsonElement number(Schema schema) {
        double value = 0;
        if (schema.getMinimum() != null && schema.getMinimum() > 0) {
            value = schema.getMinimum();
        } else if (schema.getMaximum() != null && schema.getMaximum() < 0) {
            value = schema.getMaximum();
        }
        if (value == Math.rint(value)) {
            return new JsonPrimitive((long) value);
        }
        return new JsonPrimitive(value);
    }

    private static String text(Schema schema) {
        if (schema.getExample() != null) {
            return schema.getExample();
        }
        if (!schema.getEnumValues().isEmpty()) {
            return schema.getEnumValues().get(0);
        }
        String formatted = formatted(schema.getFormat());
        if (formatted != null) {
            return formatted;
        }
        int length = schema.getMinLength() == null ? 0 : Math.min(schema.getMinLength(), MAX_PADDING);
        return "a".repeat(Math.max(length, 0));
    }

    private static String formatted(String format) {
        if (format == null) {
            return null;
        }
        return switch (format) {
            case "date" -> "1970-01-01";
            case "date-time" -> "1970-01-01T00:00:00Z";
            case "uuid" -> "00000000-0000-0000-0000-000000000000";
            case "email" -> "user@example.com";
            default -> null;
        };
    }
}
