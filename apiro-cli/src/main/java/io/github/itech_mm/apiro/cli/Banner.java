package io.github.itech_mm.apiro.cli;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

final class Banner {

    private static final String[] ART = {
        "    _          _",
        "   / \\   _ __ (_)_ __ ___",
        "  / _ \\ | '_ \\| | '__/ _ \\",
        " / ___ \\| |_) | | | | (_) |",
        "/_/   \\_\\ .__/|_|_|  \\___/",
        "        |_|"
    };

    private static final Set<String> GLOBAL_FLAGS = Set.of("--no-banner", "-q", "--quiet");

    private static final String TITLE = " :: Apiro CLI :: ";
    private static final int WIDTH = 42;

    private static final String GREEN = "\u001B[32m";
    private static final String DEFAULT_COLOR = "\u001B[39m";
    private static final String FAINT = "\u001B[2m";
    private static final String RESET = "\u001B[0;39m";

    private Banner() {
    }

    static void printIfWanted(List<String> arguments, PrintStream err) {
        Map<String, String> environment = System.getenv();
        if (err != System.err || !wanted(arguments, environment)) {
            return;
        }
        boolean terminal = System.console() != null;
        boolean color = colorEnabled(environment, System.getProperty("os.name", ""), terminal);
        err.print(render(version(), color));
        err.flush();
    }

    static List<String> withoutGlobalFlags(List<String> arguments) {
        List<String> kept = new ArrayList<>();
        boolean optionsEnded = false;
        for (String argument : arguments) {
            if (!optionsEnded && argument.equals("--")) {
                optionsEnded = true;
            } else if (!optionsEnded && GLOBAL_FLAGS.contains(argument)) {
                continue;
            }
            kept.add(argument);
        }
        return kept;
    }

    static boolean wanted(List<String> arguments, Map<String, String> environment) {
        String setting = environment.getOrDefault("APIRO_BANNER", "").trim().toLowerCase(Locale.ROOT);
        if (setting.equals("off") || setting.equals("false") || setting.equals("0") || setting.equals("no")) {
            return false;
        }
        for (String argument : arguments) {
            switch (argument) {
                case "--":
                    return true;
                case "--json":
                case "-q":
                case "--quiet":
                case "--no-banner":
                    return false;
                default:
                    break;
            }
        }
        return true;
    }

    static boolean colorEnabled(Map<String, String> environment, String osName, boolean terminal) {
        String noColor = environment.get("NO_COLOR");
        if (noColor != null && !noColor.isEmpty()) {
            return false;
        }
        String force = environment.getOrDefault("FORCE_COLOR", "");
        if (!force.isEmpty() && !force.equals("0") && !force.equalsIgnoreCase("false")) {
            return true;
        }
        return terminal && useColor(environment, osName);
    }

    static boolean useColor(Map<String, String> environment, String osName) {
        String noColor = environment.get("NO_COLOR");
        if (noColor != null && !noColor.isEmpty()) {
            return false;
        }
        String term = environment.getOrDefault("TERM", "");
        if (term.equals("dumb")) {
            return false;
        }
        if (osName.toLowerCase(Locale.ROOT).contains("win")) {
            return environment.containsKey("WT_SESSION")
                    || environment.containsKey("ANSICON")
                    || "ON".equalsIgnoreCase(environment.get("ConEmuANSI"))
                    || environment.containsKey("TERM_PROGRAM")
                    || !term.isEmpty();
        }
        return true;
    }

    static String render(String version, boolean color) {
        StringBuilder text = new StringBuilder();
        text.append(System.lineSeparator());
        for (String line : ART) {
            if (color) {
                text.append(GREEN).append(line).append(DEFAULT_COLOR);
            } else {
                text.append(line);
            }
            text.append(System.lineSeparator());
        }
        String versionText = " (v" + version + ")";
        String padding = " ".repeat(Math.max(1, WIDTH - TITLE.length() - versionText.length()));
        if (color) {
            text.append(GREEN).append(TITLE).append(DEFAULT_COLOR)
                    .append(padding)
                    .append(FAINT).append(versionText).append(RESET);
        } else {
            text.append(TITLE).append(padding).append(versionText);
        }
        text.append(System.lineSeparator()).append(System.lineSeparator());
        return text.toString();
    }

    static String version() {
        try (InputStream stream = Banner.class.getResourceAsStream("/apiro-cli.properties")) {
            if (stream != null) {
                Properties properties = new Properties();
                properties.load(stream);
                String value = properties.getProperty("version", "").trim();
                if (!value.isEmpty() && !value.startsWith("${")) {
                    return value;
                }
            }
        } catch (IOException ignored) {
        }
        return "dev";
    }
}