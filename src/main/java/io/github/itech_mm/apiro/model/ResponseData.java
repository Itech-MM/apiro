package io.github.itech_mm.apiro.model;

import java.util.List;

public record ResponseData(int status, String statusText, long totalMillis, long headersMillis, long sizeBytes,
                           int attempts, List<KeyValue> headers, List<KeyValue> cookies, String body) {

    public ResponseData {
        statusText = statusText == null ? "" : statusText;
        attempts = Math.max(1, attempts);
        headers = headers == null ? List.of() : List.copyOf(headers);
        cookies = cookies == null ? List.of() : List.copyOf(cookies);
        body = body == null ? "" : body;
    }
}