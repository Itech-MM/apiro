package io.github.itech_mm.apiro.ui.editor;

import io.github.itech_framework.code_editor.completion.CompletionItem;
import io.github.itech_framework.code_editor.completion.CompletionProvider;
import io.github.itech_mm.apiro.variables.VariableScope;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class VariableCompletionProvider implements CompletionProvider {

    @Override
    public List<CompletionItem> complete(String text, int caret) {
        if (text == null || caret < 2 || caret > text.length()) {
            return List.of();
        }
        int open = text.lastIndexOf("${", caret - 1);
        if (open < 0) {
            return List.of();
        }
        int start = open + 2;
        String typed = text.substring(start, caret);
        for (int i = 0; i < typed.length(); i++) {
            char c = typed.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '_' || c == '.' || c == '-')) {
                return List.of();
            }
        }
        boolean closed = caret < text.length() && text.charAt(caret) == '}';
        String needle = typed.toLowerCase(Locale.ROOT);
        List<CompletionItem> items = new ArrayList<>();
        for (Map.Entry<String, String> entry : VariableScope.current().entrySet()) {
            String name = entry.getKey();
            if (!name.toLowerCase(Locale.ROOT).startsWith(needle)) {
                continue;
            }
            items.add(new CompletionItem(name, closed ? name : name + "}", CompletionItem.Kind.VARIABLE, start,
                    caret));
        }
        return items;
    }

    @Override
    public String triggerCharacters() {
        return "{$";
    }
}