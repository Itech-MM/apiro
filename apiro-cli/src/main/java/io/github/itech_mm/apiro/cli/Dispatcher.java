package io.github.itech_mm.apiro.cli;

import io.github.itech_framework.core.annotations.components.levels.Presentation;
import io.github.itech_framework.core.annotations.dependency.Use;
import io.github.itech_framework.core.annotations.methods.OnInit;
import java.io.PrintStream;
import java.util.List;

@Presentation
public class Dispatcher {

    @Use
    private ListCommand listCommand;

    private List<Command> commands = List.of();

    @OnInit
    private void collectCommands() {
        commands = List.of(listCommand);
    }

    int dispatch(List<String> raw, PrintStream out, PrintStream err) {
        Banner.printIfWanted(raw, err);
        raw = Banner.withoutGlobalFlags(raw);
        if (raw.isEmpty()) {
            err.print(Usage.general(commands));
            return 2;
        }
        String first = raw.get(0);
        if (isHelp(first) || first.equals("help")) {
            return help(raw, out, err);
        }
        Command command = find(first);
        if (command == null) {
            err.println("apiro: unknown command '" + first + "'");
            err.print(Usage.general(commands));
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

    static String describe(Exception error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }

    private int help(List<String> raw, PrintStream out, PrintStream err) {
        if (raw.size() < 2 || isHelp(raw.get(1))) {
            out.print(Usage.general(commands));
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

    private Command find(String name) {
        for (Command command : commands) {
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
}
