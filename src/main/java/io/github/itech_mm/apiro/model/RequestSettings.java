package io.github.itech_mm.apiro.model;

public record RequestSettings(Integer connectTimeoutMillis, Integer socketTimeoutMillis, Boolean followRedirects,
                              Integer maxRedirects, String proxyHost, Integer proxyPort) {

    public static RequestSettings defaults() {
        return new RequestSettings(null, null, null, null, null, null);
    }
}