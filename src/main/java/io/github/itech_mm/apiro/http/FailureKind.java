package io.github.itech_mm.apiro.http;

public enum FailureKind {
    INVALID_REQUEST,
    FILE_MISSING,
    TIMEOUT,
    UNKNOWN_HOST,
    CONNECTION,
    TLS,
    BODY_TOO_LARGE,
    OTHER
}