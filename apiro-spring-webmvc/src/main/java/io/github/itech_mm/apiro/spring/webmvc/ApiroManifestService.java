package io.github.itech_mm.apiro.spring.webmvc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

public final class ApiroManifestService {
    private static final Log LOG = LogFactory.getLog(ApiroManifestService.class);
    private static final String HANDLER_MAPPING_BEAN = "requestMappingHandlerMapping";

    private final ApplicationContext context;
    private final ApiroSettings settings;
    private String json;

    public ApiroManifestService(ApplicationContext context, ApiroSettings settings) {
        this.context = context;
        this.settings = settings;
    }

    public synchronized String json() {
        if (json == null) {
            json = build();
        }
        return json;
    }

    public void export() {
        String file = settings.getExportFile();
        if (file == null) {
            return;
        }
        try {
            Path path = Paths.get(file).toAbsolutePath();
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(path, json().getBytes(StandardCharsets.UTF_8));
            restrict(path);
            LOG.info("Apiro manifest written to " + path);
        } catch (IOException | RuntimeException | LinkageError error) {
            LOG.warn("Apiro manifest export to " + file + " failed: " + error);
        }
    }

    private String build() {
        ManifestBuilder.Result result = new ManifestBuilder()
                .applicationName(settings.getApplicationName())
                .contextPath(settings.getContextPath())
                .servletPath(settings.getServletPath())
                .excludePackages(settings.getExcludePackages())
                .authentication(settings.getAuthentication())
                .build(RequestMappings.describe(handlerMapping().getHandlerMethods()));
        for (String warning : result.getWarnings()) {
            LOG.warn("Apiro manifest: " + warning);
        }
        return ManifestJsonWriter.write(result.getManifest());
    }

    private RequestMappingHandlerMapping handlerMapping() {
        if (context.containsBean(HANDLER_MAPPING_BEAN)) {
            return context.getBean(HANDLER_MAPPING_BEAN, RequestMappingHandlerMapping.class);
        }
        return BeanFactoryUtils.beanOfTypeIncludingAncestors(context, RequestMappingHandlerMapping.class);
    }

    private static void restrict(Path path) {
        try {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException | IOException ignored) {
            return;
        }
    }
}
