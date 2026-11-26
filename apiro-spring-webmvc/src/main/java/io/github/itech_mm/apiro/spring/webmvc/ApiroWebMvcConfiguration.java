package io.github.itech_mm.apiro.spring.webmvc;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApiroWebMvcConfiguration {
    @Bean
    public ApiroManifestController apiroManifestController() {
        return new ApiroManifestController();
    }
}