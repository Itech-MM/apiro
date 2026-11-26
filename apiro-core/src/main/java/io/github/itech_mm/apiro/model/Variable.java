package io.github.itech_mm.apiro.model;

public record Variable(String key, String value, boolean enabled, boolean secret) {

    public Variable {
        key = key == null ? "" : key;
        value = value == null ? "" : value;
    }

    public static Variable of(String key, String value) {
        return new Variable(key, value, true, false);
    }

    public static Variable secret(String key) {
        return new Variable(key, "", true, true);
    }
}