package io.github.itech_mm.apiro.model;

public record RunStep(String stepId, String name, String state, int status, long millis, String message,
                      String detail) {

    public RunStep {
        stepId = stepId == null ? "" : stepId;
        name = name == null ? "" : name;
        state = state == null ? "" : state;
        status = Math.max(0, status);
        millis = Math.max(0, millis);
        message = message == null ? "" : message;
        detail = detail == null ? "" : detail;
    }
}
