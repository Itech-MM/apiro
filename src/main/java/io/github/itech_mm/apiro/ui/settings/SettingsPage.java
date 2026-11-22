package io.github.itech_mm.apiro.ui.settings;

import javafx.scene.Node;

public record SettingsPage(String title, Node content, Runnable commit) {

    public SettingsPage(String title, Node content) {
        this(title, content, () -> {
        });
    }
}