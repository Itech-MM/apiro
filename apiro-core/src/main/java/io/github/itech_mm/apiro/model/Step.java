package io.github.itech_mm.apiro.model;

import java.util.List;
import java.util.UUID;

public record Step(String id, String requestId, String name, List<Extractor> extractors,
                   List<Assertion> assertions, List<KeyValue> headers, List<KeyValue> query,
                   String urlOverride, int delayMillis, OnFailure onFailure, double x, double y,
                   BodyOverride bodyOverride) {

    public Step {
        id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
        requestId = requestId == null ? "" : requestId;
        name = name == null ? "" : name;
        extractors = extractors == null ? List.of() : List.copyOf(extractors);
        assertions = assertions == null ? List.of() : List.copyOf(assertions);
        headers = headers == null ? List.of() : List.copyOf(headers);
        query = query == null ? List.of() : List.copyOf(query);
        urlOverride = urlOverride == null ? "" : urlOverride;
        delayMillis = Math.max(0, delayMillis);
    }

    public Step(String id, String requestId, String name, List<Extractor> extractors,
                List<Assertion> assertions, List<KeyValue> headers, int delayMillis, OnFailure onFailure,
                double x, double y, BodyOverride bodyOverride) {
        this(id, requestId, name, extractors, assertions, headers, null, "", delayMillis, onFailure, x, y,
                bodyOverride);
    }

    public Step(String id, String requestId, String name, List<Extractor> extractors,
                List<Assertion> assertions, List<KeyValue> headers, int delayMillis, OnFailure onFailure,
                double x, double y) {
        this(id, requestId, name, extractors, assertions, headers, delayMillis, onFailure, x, y, null);
    }

    public Step(String id, String requestId, String name, List<Extractor> extractors,
                List<Assertion> assertions, int delayMillis, OnFailure onFailure, double x, double y) {
        this(id, requestId, name, extractors, assertions, null, delayMillis, onFailure, x, y);
    }

    public static Step of(String requestId, String name) {
        return new Step(null, requestId, name, null, null, null, 0, null, 0, 0);
    }

    public boolean hasManualPosition() {
        return x != 0 || y != 0;
    }

    public Step withPosition(double newX, double newY) {
        return new Step(id, requestId, name, extractors, assertions, headers, query, urlOverride, delayMillis,
                onFailure, newX, newY, bodyOverride);
    }
}
