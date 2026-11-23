package io.github.itech_mm.apiro.ui.editor;

import io.github.itech_framework.code_editor.CodeEditor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class VariableStyler {

    public static final String STYLE = "var-ref";

    private static final Pattern REFERENCE = Pattern.compile("\\$\\{\\s*[A-Za-z0-9_.\\-]+\\s*}");

    private VariableStyler() {
    }

    public static void install(CodeEditor editor) {
        boolean[] applying = {false};
        Runnable apply = () -> {
            if (applying[0]) {
                return;
            }
            applying[0] = true;
            try {
                String text = editor.getText();
                if (text == null || text.indexOf("${") < 0) {
                    return;
                }
                Matcher matcher = REFERENCE.matcher(text);
                while (matcher.find()) {
                    editor.addStyleClass(matcher.start(), matcher.end(), STYLE);
                }
            } finally {
                applying[0] = false;
            }
        };
        editor.textProperty().addListener((obs, old, now) -> apply.run());
        editor.addHighlightListener(apply);
    }
}