package io.github.itech_mm.apiro.model;

import java.util.List;

public record BodyOverride(Mode mode, String text, List<FieldOverride> fields) {

    public enum Mode {
        PATCH, REPLACE
    }

    public BodyOverride {
        mode = mode == null ? Mode.PATCH : mode;
        text = text == null ? "" : text;
        fields = fields == null ? List.of() : List.copyOf(fields);
    }

    public static BodyOverride replace(String text) {
        return new BodyOverride(Mode.REPLACE, text, null);
    }

    public static BodyOverride patch(List<FieldOverride> fields) {
        return new BodyOverride(Mode.PATCH, "", fields);
    }
}
