package io.github.itech_mm.apiro.ui.editor;

import io.github.itech_framework.code_editor.language.LanguageRegistry;
import io.github.itech_framework.code_editor.language.template.TemplateLanguages;
import io.github.itech_framework.code_editor.language.template.TemplateLoader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class ScenarioLogLanguage {

    public static final String ID = "apiro-log";

    private static final String RESOURCE = "/languages/apiro-log.json";

    private static boolean registered;

    private ScenarioLogLanguage() {
    }

    public static synchronized void ensureRegistered() {
        if (registered) {
            return;
        }
        try (InputStream in = ScenarioLogLanguage.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing " + RESOURCE);
            }
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            LanguageRegistry.getDefault().register(TemplateLanguages.fromTemplate(TemplateLoader.load(RESOURCE, json)));
            registered = true;
        } catch (IOException error) {
            throw new IllegalStateException("Cannot read " + RESOURCE, error);
        }
    }
}
