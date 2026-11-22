package io.github.itech_mm.apiro.http;

public record ClientSettings(int maxRetries, long maxBodyBytes, boolean sslVerification) {

    public static final long DEFAULT_MAX_BODY_BYTES = 50L * 1024 * 1024;

    public ClientSettings {
        maxRetries = Math.max(0, maxRetries);
        maxBodyBytes = Math.max(0, maxBodyBytes);
    }

    public static ClientSettings defaults() {
        return new ClientSettings(0, DEFAULT_MAX_BODY_BYTES, true);
    }
}