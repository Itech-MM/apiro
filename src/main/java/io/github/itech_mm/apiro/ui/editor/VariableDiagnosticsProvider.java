package io.github.itech_mm.apiro.ui.editor;

import io.github.itech_framework.code_editor.diagnostics.Diagnostic;
import io.github.itech_framework.code_editor.diagnostics.DiagnosticSeverity;
import io.github.itech_framework.code_editor.diagnostics.DiagnosticsProvider;
import io.github.itech_mm.apiro.service.variables.VariableScope;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class VariableDiagnosticsProvider implements DiagnosticsProvider {

    private static final Pattern REFERENCE = Pattern.compile("\\$\\{\\s*([A-Za-z0-9_.\\-]+)\\s*}");

    @Override
    public List<Diagnostic> diagnose(String text) {
        if (text == null || text.indexOf("${") < 0) {
            return List.of();
        }
        Map<String, String> scope = VariableScope.current();
        List<Diagnostic> result = new ArrayList<>();
        Matcher matcher = REFERENCE.matcher(text);
        while (matcher.find()) {
            String name = matcher.group(1);
            if (!scope.containsKey(name)) {
                result.add(new Diagnostic(matcher.start(), matcher.end(), DiagnosticSeverity.WARNING,
                        "Undefined variable: " + name));
            }
        }
        return result;
    }
}