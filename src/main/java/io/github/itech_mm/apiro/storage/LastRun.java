package io.github.itech_mm.apiro.storage;

public record LastRun(long at, int status, long millis) {

    public LastRun {
        at = Math.max(0, at);
        status = Math.max(0, status);
        millis = Math.max(0, millis);
    }
}