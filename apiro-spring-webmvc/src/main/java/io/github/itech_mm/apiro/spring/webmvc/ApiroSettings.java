package io.github.itech_mm.apiro.spring.webmvc;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.core.env.Environment;

public final class ApiroSettings {
    private static final Set<String> DEVELOPMENT_PROFILES = new LinkedHashSet<>(
            Arrays.asList("dev", "development", "local", "test"));

    private final String applicationName;
    private final String contextPath;
    private final String servletPath;
    private final List<String> excludePackages;
    private final List<String> authentication;
    private final String exportFile;
    private final String token;
    private final boolean tokenGenerated;
    private final List<String> activeProfiles;

    private ApiroSettings(Environment environment) {
        this.applicationName = text(environment.getProperty("spring.application.name"));
        this.contextPath = path(first(environment, "apiro.context-path", "server.servlet.context-path",
                "server.context-path"));
        this.servletPath = path(first(environment, "apiro.servlet-path", "spring.mvc.servlet.path"));
        this.excludePackages = packages(list(environment, "apiro.exclude-packages"));
        this.authentication = list(environment, "apiro.security.authentication");
        this.exportFile = text(environment.getProperty("apiro.export-file"));
        String configured = text(environment.getProperty("apiro.token"));
        this.tokenGenerated = configured == null;
        this.token = configured != null ? configured : generate();
        this.activeProfiles = Collections.unmodifiableList(Arrays.asList(environment.getActiveProfiles()));
    }

    public static ApiroSettings from(Environment environment) {
        return new ApiroSettings(environment);
    }

    public String getApplicationName() {
        return applicationName;
    }

    public String getContextPath() {
        return contextPath;
    }

    public String getServletPath() {
        return servletPath;
    }

    public List<String> getExcludePackages() {
        return excludePackages;
    }

    public List<String> getAuthentication() {
        return authentication;
    }

    public String getExportFile() {
        return exportFile;
    }

    public String getToken() {
        return token;
    }

    public boolean isTokenGenerated() {
        return tokenGenerated;
    }

    public List<String> getActiveProfiles() {
        return activeProfiles;
    }

    public boolean isNonDevelopmentProfile() {
        if (activeProfiles.isEmpty()) {
            return false;
        }
        for (String profile : activeProfiles) {
            if (DEVELOPMENT_PROFILES.contains(profile.toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        return true;
    }

    public boolean accepts(String candidate) {
        if (candidate == null) {
            return false;
        }
        return MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),
                candidate.getBytes(StandardCharsets.UTF_8));
    }

    private static String generate() {
        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String first(Environment environment, String... keys) {
        for (String key : keys) {
            String value = environment.getProperty(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String text(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String path(String value) {
        String trimmed = text(value);
        if (trimmed == null) {
            return null;
        }
        if (trimmed.endsWith("/*")) {
            trimmed = trimmed.substring(0, trimmed.length() - 2);
        }
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.startsWith("/") ? trimmed : "/" + trimmed;
    }

    private static List<String> list(Environment environment, String key) {
        Set<String> values = new LinkedHashSet<>();
        String joined = environment.getProperty(key);
        if (joined != null) {
            for (String part : joined.split(",")) {
                add(values, part);
            }
        }
        for (int index = 0; ; index++) {
            String item = environment.getProperty(key + "[" + index + "]");
            if (item == null) {
                break;
            }
            add(values, item);
        }
        return new ArrayList<>(values);
    }

    private static void add(Set<String> values, String raw) {
        String value = text(raw);
        if (value != null) {
            values.add(value);
        }
    }

    private static List<String> packages(List<String> raw) {
        List<String> result = new ArrayList<>();
        for (String value : raw) {
            String name = value;
            while (name.endsWith(".") || name.endsWith("*")) {
                name = name.substring(0, name.length() - 1);
            }
            if (!name.isEmpty() && !result.contains(name)) {
                result.add(name);
            }
        }
        return result;
    }
}
