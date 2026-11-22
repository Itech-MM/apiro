package io.github.itech_mm.apiro.ui;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import javafx.scene.text.Font;

public final class MonoFont {

    private static final List<String> CANDIDATES = List.of("JetBrains Mono", "Cascadia Mono", "Cascadia Code",
            "Consolas", "SF Mono", "Menlo", "DejaVu Sans Mono", "Liberation Mono");

    private MonoFont() {
    }
    
    public static final double EDITOR_SIZE = 13;

    private static final List<String> FILES = List.of("JetBrainsMono-Regular.ttf", "JetBrainsMono-Bold.ttf",
            "JetBrainsMono-Italic.ttf", "JetBrainsMono-BoldItalic.ttf");

    public static void load() {
        for (String file : FILES) {
            try (InputStream stream = MonoFont.class.getResourceAsStream("/static/fonts/" + file)) {
                if (stream != null) {
                    Font.loadFont(stream, EDITOR_SIZE);
                }
            } catch (IOException error) {
                continue;
            }
        }
    }

    public static String family() {
        List<String> installed = Font.getFamilies();
        return CANDIDATES.stream().filter(installed::contains).findFirst().orElse("Monospaced");
    }

    public static String stylesheet() {
        String css = ".url-bar, .url-bar .text-field.url-field, .url-bar .protocol-label,"
                + " .section-row .section-preview, .section-row .content-type-label, .kv-table .text-field.kv-field"
                + " { -fx-font-family: \"" + family() + "\"; }";
        return "data:text/css;base64," + Base64.getEncoder().encodeToString(css.getBytes(StandardCharsets.UTF_8));
    }
}