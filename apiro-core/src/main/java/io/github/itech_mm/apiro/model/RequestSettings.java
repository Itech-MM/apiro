package io.github.itech_mm.apiro.model;

public record RequestSettings(Integer connectTimeoutMillis, Integer socketTimeoutMillis, Boolean followRedirects,
                              Integer maxRedirects, String proxyHost, Integer proxyPort) {

    public static RequestSettings defaults() {
        return new RequestSettings(null, null, null, null, null, null);
    }
    
    public RequestSettings withDefaults() {
        return new RequestSettings(
                connectTimeoutMillis == null ? 10000 : connectTimeoutMillis,
                socketTimeoutMillis == null ? 30000 : socketTimeoutMillis,
                followRedirects == null ? Boolean.TRUE : followRedirects,
                maxRedirects == null ? 5 : maxRedirects,
                proxyHost == null ? "" : proxyHost,
                proxyPort);
    }
}