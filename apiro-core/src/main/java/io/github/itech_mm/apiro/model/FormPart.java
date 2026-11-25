package io.github.itech_mm.apiro.model;

public record FormPart(String name, PartKind kind, String value, String filePath, boolean enabled) {

    public FormPart {
        name = name == null ? "" : name;
        kind = kind == null ? PartKind.TEXT : kind;
        value = value == null ? "" : value;
        filePath = filePath == null ? "" : filePath;
    }

    public static FormPart text(String name, String value) {
        return new FormPart(name, PartKind.TEXT, value, "", true);
    }

    public static FormPart file(String name, String filePath) {
        return new FormPart(name, PartKind.FILE, "", filePath, true);
    }
}