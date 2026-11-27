package io.github.itech_mm.apiro.spring.webmvc;

import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class ApiroWebMvcConfiguration {
    @Bean
    public ApiroSettings apiroSettings(Environment environment) {
        return ApiroSettings.from(environment);
    }

    @Bean
    public ApiroManifestService apiroManifestService(ApplicationContext context, ApiroSettings settings) {
        return new ApiroManifestService(context, settings);
    }

    @Bean
    public ApiroManifestController apiroManifestController(ApiroManifestService service, ApiroSettings settings) {
        return new ApiroManifestController(service, settings);
    }

    @Bean
    public ApiroStartup apiroStartup(ApplicationContext context, ApiroSettings settings,
                                     ApiroManifestService service) {
        return new ApiroStartup(context, settings, service);
    }
}
