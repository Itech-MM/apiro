package io.github.itech_mm.apiro.spring.boot;

import io.github.itech_mm.apiro.spring.webmvc.ApiroWebMvcConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "apiro", name = "enabled", havingValue = "true")
@ConditionalOnClass(name = "org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping")
@Import(ApiroWebMvcConfiguration.class)
public class ApiroAutoConfiguration {
}