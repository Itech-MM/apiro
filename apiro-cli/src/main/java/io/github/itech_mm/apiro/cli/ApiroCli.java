package io.github.itech_mm.apiro.cli;

import io.github.itech_framework.core.ITechApplication;
import io.github.itech_framework.core.annotations.ComponentScan;
import io.github.itech_framework.core.annotations.api_client.EnableApiClient;
import io.github.itech_framework.core.annotations.dependency.Use;
import io.github.itech_framework.core.processor.components_processor.ApplicationContext;
import io.github.itech_framework.core.processor.components_processor.ComponentProcessor;
import java.io.PrintStream;
import java.util.List;

@ComponentScan(basePackage = "io.github.itech_mm.apiro", properties = {})
@EnableApiClient
public class ApiroCli {

    @Use
    private Dispatcher dispatcher;

    public static void main(String[] args) {
        System.exit(start(args, System.out, System.err));
    }

    static int start(String[] args, PrintStream out, PrintStream err) {
        try (ApplicationContext context = ITechApplication.run(ApiroCli.class)) {
            ApiroCli application = new ApiroCli();
            ComponentProcessor.injectMethods(ApiroCli.class, application, ComponentProcessor.PRESENTATION_LEVEL);
            return application.dispatcher.dispatch(List.of(args), out, err);
        } catch (Exception error) {
            err.println("apiro: could not start: " + Dispatcher.describe(error));
            return 1;
        }
    }
}
