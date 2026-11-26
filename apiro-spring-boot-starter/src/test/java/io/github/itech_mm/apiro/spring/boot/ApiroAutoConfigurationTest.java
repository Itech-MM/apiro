package io.github.itech_mm.apiro.spring.boot;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.itech_mm.apiro.spring.webmvc.ApiroManifestController;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ApiroAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ApiroAutoConfiguration.class));

    @Test
    void offByDefault() {
        runner.run(ctx -> assertEquals(0, ctx.getBeansOfType(ApiroManifestController.class).size()));
    }

    @Test
    void onWhenEnabled() {
        runner.withPropertyValues("apiro.enabled=true")
                .run(ctx -> assertEquals(1, ctx.getBeansOfType(ApiroManifestController.class).size()));
    }
}