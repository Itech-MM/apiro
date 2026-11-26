package io.github.itech_mm.apiro.spring.webmvc;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.itech_mm.apiro.spec.ManifestVersion;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;

class EnableApiroTest {

    @Configuration
    @EnableApiro
    static class Config {
    }

    @Test
    void registersManifestController() {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(Config.class)) {
            String json = ctx.getBean(ApiroManifestController.class).manifest();
            assertTrue(json.contains("\"manifestVersion\":" + ManifestVersion.CURRENT));
        }
    }
}