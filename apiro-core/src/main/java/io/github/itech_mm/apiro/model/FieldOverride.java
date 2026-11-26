package io.github.itech_mm.apiro.model;

public record FieldOverride(String path, ValueType type, String value, boolean enabled) {

    public enum ValueType {
        STRING, NUMBER, BOOLEAN, NULL, JSON, REMOVE
    }

    public FieldOverride {
        path = path == null ? "" : path.trim();
        type = type == null ? ValueType.STRING : type;
        value = value == null ? "" : value;
    }

    public static FieldOverride of(String path, ValueType type, String value) {
        return new FieldOverride(path, type, value, true);
    }
}
