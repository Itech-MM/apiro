package io.github.itech_mm.apiro.spring.webmvc;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Import;

/** Turns on Apiro in a plain Spring MVC application (no Spring Boot needed). */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
@Import(ApiroWebMvcConfiguration.class)
public @interface EnableApiro {
}