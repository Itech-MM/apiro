package io.github.itech_mm.apiro.cli;

import io.github.itech_framework.core.annotations.components.levels.Presentation;
import io.github.itech_framework.core.annotations.dependency.Use;
import io.github.itech_framework.core.annotations.methods.OnInit;

/**
 * Runs the command the user typed. The framework creates it, injects the fields and calls
 * {@link #run()} once, after {@link Dispatcher} and its commands are ready.
 */
@Presentation
public class CliRunner {

    @Use
    private CliSession session;

    @Use
    private Dispatcher dispatcher;

    @OnInit
    private void run() {
        session.exitCode(dispatcher.dispatch(session.arguments(), session.out(), session.err()));
    }
}
