package io.github.itech_mm.apiro.ui.settings;

import java.util.function.Supplier;
import javafx.scene.Node;

public record SettingsPage(String title, Node content, Runnable commit, Supplier<String> problem) {

    public SettingsPage(String title, Node content, Runnable commit) {
        this(title, content, commit, () -> "");
    }

    public SettingsPage(String title, Node content) {
        this(title, content, () -> {
        });
    }
}