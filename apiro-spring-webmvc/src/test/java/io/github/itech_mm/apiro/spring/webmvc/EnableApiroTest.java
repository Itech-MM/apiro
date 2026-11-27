package io.github.itech_mm.apiro.spring.webmvc;

import static org.junit.jupiter.api.Assertions.assertNotNull;

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
            assertNotNull(ctx.getBean(ApiroManifestController.class));
            assertNotNull(ctx.getBean(ApiroSettings.class).getToken());
        }
    }
}
