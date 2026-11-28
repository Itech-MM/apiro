package io.github.itech_mm.apiro.cli;

import java.io.PrintStream;
import java.util.List;

public final class ApiroCli {

    private static final List<Command> COMMANDS = List.of(new ListCommand());

    private ApiroCli() {
    }

    public static void main(String[] args) {
        System.exit(execute(args, System.out, System.err));
    }

    static int execute(String[] args, PrintStream out, PrintStream err) {
        List<String> raw = List.of(args);
        if (raw.isEmpty()) {
            err.print(Usage.general(COMMANDS));
            return 2;
        }
        String first = raw.get(0);
        if (isHelp(first) || first.equals("help")) {
            return help(raw, out, err);
        }
        Command command = find(first);
        if (command == null) {
            err.println("apiro: unknown command '" + first + "'");
            err.print(Usage.general(COMMANDS));
            return 2;
        }
        List<String> rest = raw.subList(1, raw.size());
        if (wantsHelp(rest)) {
            out.print(command.usage());
            return 0;
        }
        try {
            Arguments arguments = Arguments.parse(rest, command.valueOptions(), command.flagOptions());
            return command.run(arguments, out, err);
        } catch (CliException error) {
            err.println("apiro: " + error.getMessage());
            err.println("Run 'apiro help " + command.name() + "' for usage.");
            return 2;
        } catch (Exception error) {
            err.println("apiro: " + describe(error));
            return 1;
        }
    }

    private static int help(List<String> raw, PrintStream out, PrintStream err) {
        if (raw.size() < 2 || isHelp(raw.get(1))) {
            out.print(Usage.general(COMMANDS));
            return 0;
        }
        Command command = find(raw.get(1));
        if (command == null) {
            err.println("apiro: unknown command '" + raw.get(1) + "'");
            return 2;
        }
        out.print(command.usage());
        return 0;
    }

    private static Command find(String name) {
        for (Command command : COMMANDS) {
            if (command.name().equals(name)) {
                return command;
            }
        }
        return null;
    }

    private static boolean wantsHelp(List<String> tokens) {
        for (String token : tokens) {
            if (token.equals("--")) {
                return false;
            }
            if (isHelp(token)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isHelp(String token) {
        return token.equals("-h") || token.equals("--help");
    }

    private static String describe(Exception error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }
}
