package io.github.itech_mm.apiro.ui;

import io.github.itech_framework.code_editor.CodeEditor;
import io.github.itech_framework.code_editor.theme.EditorTheme;

public final class EditorStyle {

    private static final String STYLESHEET = "/static/css/editor.css";

    private static EditorTheme lightTheme;

    private EditorStyle() {
    }

    public static void apply(CodeEditor editor) {
        editor.setTheme(lightTheme());
        editor.setFontSize(MonoFont.EDITOR_SIZE);
        editor.setHighlightCurrentLine(false);
        editor.getStylesheets().add(EditorStyle.class.getResource(STYLESHEET).toExternalForm());
    }

    private static EditorTheme lightTheme() {
        if (lightTheme == null) {
            lightTheme = EditorTheme.builder("apiro-light", "Apiro Light", false)
                    .extending("light-plus")
                    .color("background", "#FFFFFF")
                    .color("foreground", "#18181B")
                    .color("caret", "#18181B")
                    .color("line-number", "#A1A1AA")
                    .color("line-number-active", "#A1A1AA")
                    .color("token-variable", "#871094")
                    .color("token-string", "#067D17")
                    .color("token-number", "#1750EB")
                    .color("token-constant", "#0033B3")
                    .color("token-keyword", "#0033B3")
                    .color("token-punctuation", "#18181B")
                    .color("token-operator", "#18181B")
                    .color("token-comment", "#8C8C94")
                    .build();
        }
        return lightTheme;
    }
}