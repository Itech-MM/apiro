package io.github.itech_mm.apiro.ui.editor;

import io.github.itech_framework.code_editor.diagnostics.Diagnostic;
import io.github.itech_framework.code_editor.diagnostics.DiagnosticSeverity;
import io.github.itech_framework.code_editor.diagnostics.DiagnosticsProvider;
import io.github.itech_mm.apiro.service.variables.DynamicVariables;
import io.github.itech_mm.apiro.service.variables.VariableResolver;
import io.github.itech_mm.apiro.service.variables.VariableScope;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

public final class VariableDiagnosticsProvider implements DiagnosticsProvider {

    @Override
    public List<Diagnostic> diagnose(String text) {
        if (text == null || text.indexOf("${") < 0) {
            return List.of();
        }
        Map<String, String> scope = VariableScope.current();
        List<Diagnostic> result = new ArrayList<>();
        Matcher matcher = VariableResolver.REFERENCE.matcher(text);
        while (matcher.find()) {
            String name = matcher.group(1);
            if (scope.containsKey(name) || DynamicVariables.isKnown(name)) {
                continue;
            }
            String label = DynamicVariables.isDynamic(name) ? "Unknown dynamic variable: " : "Undefined variable: ";
            result.add(new Diagnostic(matcher.start(), matcher.end(), DiagnosticSeverity.WARNING, label + name));
        }
        return result;
    }
}