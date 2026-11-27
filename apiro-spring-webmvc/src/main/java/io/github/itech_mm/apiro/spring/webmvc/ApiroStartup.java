package io.github.itech_mm.apiro.spring.webmvc;

import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;

public final class ApiroStartup implements ApplicationListener<ContextRefreshedEvent> {
    private static final Log LOG = LogFactory.getLog(ApiroStartup.class);

    private final ApplicationContext context;
    private final ApiroSettings settings;
    private final ApiroManifestService service;
    private final AtomicBoolean done = new AtomicBoolean();

    public ApiroStartup(ApplicationContext context, ApiroSettings settings, ApiroManifestService service) {
        this.context = context;
        this.settings = settings;
        this.service = service;
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        if (event.getApplicationContext() != context || !done.compareAndSet(false, true)) {
            return;
        }
        String prefix = (settings.getContextPath() == null ? "" : settings.getContextPath())
                + (settings.getServletPath() == null ? "" : settings.getServletPath());
        LOG.info("Apiro manifest endpoint is enabled at " + prefix + ApiroManifestController.PATH);
        if (settings.isTokenGenerated()) {
            LOG.info("Apiro manifest token (generated, apiro.token is not set): " + settings.getToken());
        } else {
            LOG.info("Apiro manifest token is taken from apiro.token");
        }
        if (settings.isNonDevelopmentProfile()) {
            LOG.warn("Apiro manifest endpoint is enabled while profiles " + settings.getActiveProfiles()
                    + " are active. It exposes the route surface of this application.");
        }
        service.export();
    }
}
