package io.github.itech_mm.apiro.model;

public record KeyValue(String key, String value, boolean enabled) {

    public KeyValue {
        key = key == null ? "" : key;
        value = value == null ? "" : value;
    }

    public static KeyValue of(String key, String value) {
        return new KeyValue(key, value, true);
    }
}