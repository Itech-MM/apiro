package io.github.itech_mm.apiro.model;

import java.util.List;

public record RequestBody(BodyType type, String text, String contentType, String charset, List<KeyValue> form,
                          List<FormPart> parts, String filePath) {

    public RequestBody {
        type = type == null ? BodyType.NONE : type;
        text = text == null ? "" : text;
        contentType = contentType == null ? "" : contentType;
        charset = charset == null || charset.isBlank() ? "UTF-8" : charset;
        form = form == null ? List.of() : List.copyOf(form);
        parts = parts == null ? List.of() : List.copyOf(parts);
        filePath = filePath == null ? "" : filePath;
    }

    public static RequestBody none() {
        return new RequestBody(BodyType.NONE, null, null, null, null, null, null);
    }

    public static RequestBody of(BodyType type, String text) {
        return new RequestBody(type, text, null, null, null, null, null);
    }

    public String effectiveContentType() {
        return contentType.isBlank() ? type.defaultContentType() : contentType;
    }
}