package io.github.itech_mm.apiro.cli;

import java.util.List;

final class Usage {

    private Usage() {
    }

    static String general(List<Command> commands) {
        StringBuilder text = new StringBuilder();
        text.append("Usage: apiro <command> [options]\n\n");
        text.append("Commands:\n");
        for (Command command : commands) {
            text.append(String.format("  %-8s%s%n", command.name(), command.summary()));
        }
        text.append("\nGlobal options:\n");
        text.append(String.format("  %-16s%s%n", "--no-banner", "Do not print the start-up banner"));
        text.append(String.format("  %-16s%s%n", "-q, --quiet", "Same as --no-banner"));
        text.append("\nRun 'apiro help <command>' for details on a command.\n");
        return text.toString();
    }
}
