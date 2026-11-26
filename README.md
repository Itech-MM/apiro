## Modules

| Module | Purpose | Java |
|---|---|---|
| apiro-spec | Endpoint manifest model, no dependencies | 8 |
| apiro-spring-webmvc | Spring MVC detector, plain Spring, `@EnableApiro` | 8 |
| apiro-spring-boot-starter | Spring Boot auto-configuration, `apiro.enabled=true` | 8 |
| apiro-core | Models, storage, scenario runner (headless) | 17 |
| apiro-desktop | JavaFX app | 17 |

Build: `mvn verify`. CI runs the Spring modules against Boot 2.7 to 4.1 and Spring 5.3 to 7.0.