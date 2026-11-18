package io.github.itech_mm.apiro.model;

public enum BodyType {
    NONE, JSON, XML, TEXT, HTML, FORM, MULTIPART, BINARY;

    public String defaultContentType() {
        return switch (this) {
            case JSON -> "application/json";
            case XML -> "application/xml";
            case TEXT -> "text/plain";
            case HTML -> "text/html";
            case FORM -> "application/x-www-form-urlencoded";
            case MULTIPART -> "multipart/form-data";
            case BINARY -> "application/octet-stream";
            case NONE -> "";
        };
    }

    public boolean isText() {
        return this == JSON || this == XML || this == TEXT || this == HTML;
    }
}