package io.github.itech_mm.apiro.http;

import io.github.itech_framework.core.annotations.components.levels.Service;

@Service
public class RunCommand {

    private Runnable handler = () -> {
    };

    public void register(Runnable next) {
        handler = next == null ? () -> {
        } : next;
    }

    public void run() {
        handler.run();
    }
}