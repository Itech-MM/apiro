package io.github.itech_mm.apiro.ui.editor;

import io.github.itech_framework.code_editor.CodeEditor;
import io.github.itech_mm.apiro.service.variables.VariableResolver;

import java.util.regex.Matcher;

public final class VariableStyler {

    public static final String STYLE = "var-ref";

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
                int length = editor.getLength();
                if (length > 0) {
                    editor.removeStyleClass(0, length, STYLE);
                }
                String text = editor.getText();
                if (text == null || text.indexOf("${") < 0) {
                    return;
                }
                Matcher matcher = VariableResolver.REFERENCE.matcher(text);
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