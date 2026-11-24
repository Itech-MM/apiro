package io.github.itech_mm.apiro.model;

import java.util.List;
import java.util.UUID;

public record Step(String id, String requestId, String name, List<Extractor> extractors,
                   List<Assertion> assertions, int delayMillis, OnFailure onFailure, double x, double y) {

    public Step {
        id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
        requestId = requestId == null ? "" : requestId;
        name = name == null ? "" : name;
        extractors = extractors == null ? List.of() : List.copyOf(extractors);
        assertions = assertions == null ? List.of() : List.copyOf(assertions);
        delayMillis = Math.max(0, delayMillis);
    }

    public static Step of(String requestId, String name) {
        return new Step(null, requestId, name, null, null, 0, null, 0, 0);
    }
}
