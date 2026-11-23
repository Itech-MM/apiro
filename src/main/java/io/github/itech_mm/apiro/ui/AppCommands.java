package io.github.itech_mm.apiro.ui;

import io.github.itech_framework.core.annotations.components.levels.Service;
import javafx.scene.input.KeyCombination;

import java.util.EnumMap;
import java.util.Map;

@Service
public class AppCommands {

    public enum Command {
        NEW_PROJECT("Shortcut+Shift+N"),
        OPEN_PROJECT("Shortcut+O"),
        NEW_REQUEST("Shortcut+N"),
        NEW_FOLDER(null),
        SAVE("Shortcut+S");

        private final String shortcut;

        Command(String shortcut) {
            this.shortcut = shortcut;
        }

        public String shortcutText() {
            return shortcut == null ? "" : KeyCombination.keyCombination(shortcut).getDisplayText();
        }
    }

    private final Map<Command, Runnable> handlers = new EnumMap<>(Command.class);

    public void register(Command command, Runnable handler) {
        handlers.put(command, handler);
    }

    public void run(Command command) {
        Runnable handler = handlers.get(command);
        if (handler != null) {
            handler.run();
        }
    }
}