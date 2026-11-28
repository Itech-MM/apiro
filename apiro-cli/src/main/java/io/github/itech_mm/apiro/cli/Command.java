package io.github.itech_mm.apiro.cli;

import java.io.PrintStream;
import java.util.Set;

interface Command {

    String name();

    String summary();

    String usage();

    Set<String> valueOptions();

    Set<String> flagOptions();

    int run(Arguments arguments, PrintStream out, PrintStream err) throws Exception;
}
