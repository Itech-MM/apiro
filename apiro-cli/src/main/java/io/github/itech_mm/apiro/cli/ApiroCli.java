package io.github.itech_mm.apiro.cli;

import io.github.itech_framework.core.annotations.ComponentScan;
import io.github.itech_framework.core.annotations.api_client.EnableApiClient;
import io.github.itech_framework.core.processor.components_processor.ApplicationContext;
import io.github.itech_framework.core.processor.components_processor.ComponentProcessor;
import io.github.itech_framework.core.store.ComponentStore;
import java.io.PrintStream;
import java.util.List;

@ComponentScan(basePackage = "io.github.itech_mm.apiro", properties = {})
@EnableApiClient
public class ApiroCli {

    public static void main(String[] args) {
        System.exit(start(args, System.out, System.err));
    }

    static int start(String[] args, PrintStream out, PrintStream err) {
        CliSession session = new CliSession(List.of(args), out, err);
        try (ApplicationContext context = ComponentProcessor.initialize(ApiroCli.class,
                store -> store.registerComponent(CliSession.class, session, ComponentStore.DEFAULT_LEVEL))) {
            return session.exitCode();
        } catch (Exception error) {
            err.println("apiro: could not start: " + Dispatcher.describe(error));
            return 1;
        }
    }
}
