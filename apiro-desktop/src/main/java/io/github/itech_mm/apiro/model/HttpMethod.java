package io.github.itech_mm.apiro.model;

public enum HttpMethod {
    GET, POST, PUT, PATCH, DELETE, HEAD, OPTIONS;

    public String shortLabel() {
        return switch (this) {
            case DELETE -> "DEL";
            case PATCH -> "PAT";
            case OPTIONS -> "OPT";
            default -> name();
        };
    }
}