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
    private static final String INSET_LIGHT_ID = "apiro-light-inset";
    private static final String INSET_DARK_ID = "apiro-dark-inset";

    private static final BooleanProperty DARK = new SimpleBooleanProperty(storedDark());

    private static boolean lightRegistered;
    private static boolean darkRegistered;
    private static boolean insetLightRegistered;
    private static boolean insetDarkRegistered;

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

    public static void applyInset(CodeEditor editor) {
        editor.setFontSize(MonoFont.EDITOR_SIZE);
        editor.setHighlightCurrentLine(false);
        editor.getStylesheets().add(EditorStyle.class.getResource(STYLESHEET).toExternalForm());
        selectInset(editor, DARK.get());
        ChangeListener<Boolean> listener = (obs, was, dark) -> selectInset(editor, dark);
        editor.getProperties().put(LISTENER_KEY, listener);
        DARK.addListener(new WeakChangeListener<>(listener));
    }

    private static void selectInset(CodeEditor editor, boolean dark) {
        if (dark) {
            if (insetDarkRegistered) {
                editor.setTheme(INSET_DARK_ID);
            } else {
                editor.setTheme(darkTheme(INSET_DARK_ID, "Apiro Dark Inset", "#171724"));
                insetDarkRegistered = true;
            }
        } else if (insetLightRegistered) {
            editor.setTheme(INSET_LIGHT_ID);
        } else {
            editor.setTheme(lightTheme(INSET_LIGHT_ID, "Apiro Light Inset", "#F5F6F8"));
            insetLightRegistered = true;
        }
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
                editor.setTheme(darkTheme(DARK_ID, "Apiro Dark", "#232334"));
                darkRegistered = true;
            }
        } else if (lightThemeId != null) {
            editor.setTheme(lightThemeId);
        } else if (lightRegistered) {
            editor.setTheme(LIGHT_ID);
        } else {
            editor.setTheme(lightTheme(LIGHT_ID, "Apiro Light", "#FFFFFF"));
            lightRegistered = true;
        }
    }

    private static boolean storedDark() {
        Object stored = DataStorageUtil.load(RouterConfig.getDarkModeKey());
        return Boolean.parseBoolean(String.valueOf(stored).replace("\"", ""));
    }

    private static EditorTheme lightTheme(String id, String name, String background) {
        return EditorTheme.builder(id, name, false)
                .extending("light-plus")
                .color("background", background)
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

    private static EditorTheme darkTheme(String id, String name, String background) {
        return EditorTheme.builder(id, name, true)
                .extending("monokai")
                .color("background", background)
                .color("foreground", "#CDD6F4")
                .color("caret", "#F5E0DC")
                .color("selection", "rgba(203, 166, 247, 0.25)")
                .color("current-line", "#2A2B3D")
                .color("line-number", "#585B70")
                .color("line-number-active", "#BAC2DE")
                .color("scroll-thumb", "rgba(127, 132, 156, 0.35)")
                .color("scroll-thumb-hover", "rgba(127, 132, 156, 0.6)")
                .color("token-keyword", "#CBA6F7")
                .color("token-keyword-control", "#CBA6F7")
                .color("token-string", "#A6E3A1")
                .color("token-string-escape", "#F5C2E7")
                .color("token-number", "#FAB387")
                .color("token-constant", "#FAB387")
                .color("token-comment", "#7A7F97")
                .color("token-type", "#F9E2AF")
                .color("token-function", "#89B4FA")
                .color("token-variable", "#89DCEB")
                .color("token-operator", "#89DCEB")
                .color("token-punctuation", "#9399B2")
                .color("token-regexp", "#F5C2E7")
                .color("token-tag", "#F38BA8")
                .color("token-attribute", "#94E2D5")
                .color("token-invalid", "#F38BA8")
                .color("bracket-match", "rgba(249, 226, 175, 0.25)")
                .color("bracket-match-border", "#6C7086")
                .color("find-panel-background", "#181825")
                .color("find-panel-border", "#313244")
                .color("find-foreground", "#CDD6F4")
                .color("find-hint", "#7F849C")
                .color("find-field-background", "#313244")
                .color("find-field-border", "#45475A")
                .color("find-field-border-focused", "#CBA6F7")
                .color("find-hover", "rgba(255, 255, 255, 0.08)")
                .color("find-active", "rgba(203, 166, 247, 0.3)")
                .color("find-error", "#F38BA8")
                .color("find-match", "rgba(249, 226, 175, 0.3)")
                .color("find-match-current", "rgba(250, 179, 135, 0.45)")
                .color("find-match-current-border", "#FAB387")
                .color("fold-header", "rgba(49, 50, 68, 0.7)")
                .color("fold-marker", "rgba(205, 214, 244, 0.2)")
                .color("fold-marker-text", "#BAC2DE")
                .color("diag-error", "#F38BA8")
                .color("diag-warning", "#FAB387")
                .color("diag-info", "#89DCEB")
                .color("diag-tooltip-background", "#181825")
                .color("diag-tooltip-foreground", "#CDD6F4")
                .color("diag-tooltip-border", "#313244")
                .color("completion-background", "#181825")
                .color("completion-foreground", "#CDD6F4")
                .color("completion-border", "#313244")
                .color("completion-selected-background", "#3A3A5A")
                .color("completion-selected-foreground", "#FFFFFF")
                .color("completion-kind", "#7F849C")
                .build();
    }
}
