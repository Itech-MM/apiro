package io.github.itech_mm.apiro.project;

import java.nio.file.Path;

public final class ProjectSlot {

    public enum State {
        LOADING, READY, FAILED
    }

    private static final String EXTENSION = ".apiro.json";

    private static final String PREFIX = "slot:";

    private final Path file;

    private State state = State.LOADING;

    private OpenProject open;

    private String message = "";

    ProjectSlot(Path file) {
        this.file = file;
    }

    public static boolean isPlaceholderId(String id) {
        return id != null && id.startsWith(PREFIX);
    }

    public Path file() {
        return file;
    }

    public State state() {
        return state;
    }

    public OpenProject open() {
        return open;
    }

    public String message() {
        return message;
    }

    public String id() {
        return open != null ? open.id() : PREFIX + file;
    }

    public String name() {
        if (open != null) {
            return open.project().name();
        }
        String name = file.getFileName() == null ? file.toString() : file.getFileName().toString();
        return name.endsWith(EXTENSION) ? name.substring(0, name.length() - EXTENSION.length()) : name;
    }

    void loading() {
        state = State.LOADING;
        open = null;
        message = "";
    }

    void ready(OpenProject opened) {
        state = State.READY;
        open = opened;
        message = "";
    }

    void failed(String reason) {
        state = State.FAILED;
        open = null;
        message = reason == null ? "" : reason;
    }
}