package io.github.itech_mm.apiro.cli;

import java.io.PrintStream;
import java.util.List;
import java.util.Objects;

public final class CliSession {

    private final List<String> arguments;
    private final PrintStream out;
    private final PrintStream err;
    private int exitCode;

    public CliSession(List<String> arguments, PrintStream out, PrintStream err) {
        this.arguments = List.copyOf(Objects.requireNonNull(arguments));
        this.out = Objects.requireNonNull(out);
        this.err = Objects.requireNonNull(err);
    }

    List<String> arguments() {
        return arguments;
    }

    PrintStream out() {
        return out;
    }

    PrintStream err() {
        return err;
    }

    int exitCode() {
        return exitCode;
    }

    void exitCode(int exitCode) {
        this.exitCode = exitCode;
    }
}
