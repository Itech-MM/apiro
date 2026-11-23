package io.github.itech_mm.apiro.ui;

import io.github.itech_framework.code_editor.CodeEditor;
import io.github.itech_framework.code_editor.theme.EditorTheme;
import io.github.itech_framework.core.utils.DataStorageUtil;
import io.github.itech_framework.java_fx.router.config.RouterConfig;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;

public final class EditorStyle {

    private static final String STYLESHEET = "/static/css/editor.css";
    private static final String LISTENER_KEY = "apiro.editor.theme.listener";
    private static final String LIGHT_ID = "apiro-light";
    private static final String DARK_ID = "apiro-dark";

    private static final BooleanProperty DARK = new SimpleBooleanProperty(storedDark());

    private static boolean lightRegistered;
    private static boolean darkRegistered;

    private EditorStyle() {
    }

    public static BooleanProperty darkProperty() {
        return DARK;
    }

    public static void apply(CodeEditor editor) {
        follow(editor, null);
        editor.setFontSize(MonoFont.EDITOR_SIZE);
        editor.setHighlightCurrentLine(false);
        editor.getStylesheets().add(EditorStyle.class.getResource(STYLESHEET).toExternalForm());
    }

    public static void follow(CodeEditor editor, String lightThemeId) {
        select(editor, DARK.get(), lightThemeId);
        ChangeListener<Boolean> listener = (obs, was, dark) -> select(editor, dark, lightThemeId);
        editor.getProperties().put(LISTENER_KEY, listener);
        DARK.addListener(new WeakChangeListener<>(listener));
    }

    private static void select(CodeEditor editor, boolean dark, String lightThemeId) {
        if (dark) {
            if (darkRegistered) {
                editor.setTheme(DARK_ID);
            } else {
                editor.setTheme(darkTheme());
                darkRegistered = true;
            }
        } else if (lightThemeId != null) {
            editor.setTheme(lightThemeId);
        } else if (lightRegistered) {
            editor.setTheme(LIGHT_ID);
        } else {
            editor.setTheme(lightTheme());
            lightRegistered = true;
        }
    }

    private static boolean storedDark() {
        Object stored = DataStorageUtil.load(RouterConfig.getDarkModeKey());
        return Boolean.parseBoolean(String.valueOf(stored).replace("\"", ""));
    }

    private static EditorTheme lightTheme() {
        return EditorTheme.builder(LIGHT_ID, "Apiro Light", false)
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

    private static EditorTheme darkTheme() {
        return EditorTheme.builder(DARK_ID, "Apiro Dark", true)
                .extending("monokai")
                .color("background", "#272822")
                .color("foreground", "#F8F8F2")
                .color("caret", "#F8F8F0")
                .color("selection", "#49483E")
                .color("line-number", "#75715E")
                .color("line-number-active", "#908C7A")
                .color("token-variable", "#66D9EF")
                .color("token-string", "#E6DB74")
                .color("token-number", "#AE81FF")
                .color("token-constant", "#AE81FF")
                .color("token-keyword", "#F92672")
                .color("token-function", "#A6E22E")
                .color("token-type", "#66D9EF")
                .color("token-punctuation", "#F8F8F2")
                .color("token-operator", "#F92672")
                .color("token-comment", "#75715E")
                .build();
    }
}
