package io.github.itech_mm.apiro.spring.webmvc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;

class ApiroSettingsTest {
    @Test
    void readsPropertiesKnownOnlyToThePlaceholderResolver() {
        ApiroSettings settings = ApiroSettings.from(new StandardEnvironment(),
                value -> value.equals("${apiro.token:}") ? "secret" : "");

        assertEquals("secret", settings.getToken());
    }
}
