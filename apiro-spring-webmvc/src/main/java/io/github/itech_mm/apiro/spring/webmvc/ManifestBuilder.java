package io.github.itech_mm.apiro.spring.webmvc;

import io.github.itech_mm.apiro.spec.Application;
import io.github.itech_mm.apiro.spec.Conditions;
import io.github.itech_mm.apiro.spec.Endpoint;
import io.github.itech_mm.apiro.spec.Generator;
import io.github.itech_mm.apiro.spec.Handler;
import io.github.itech_mm.apiro.spec.Manifest;
import io.github.itech_mm.apiro.spec.ManifestSource;
import io.github.itech_mm.apiro.spec.ManifestVersion;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.core.SpringVersion;
import org.springframework.web.method.HandlerMethod;

public final class ManifestBuilder {
    private final Set<String> excludedPackages = new LinkedHashSet<>();
    private String applicationName;
    private String contextPath;
    private String servletPath;
    private final Set<String> authenticationSchemes = new TreeSet<>(CodePoints.ORDER);

    public static final class Result {
        private final Manifest manifest;
        private final List<String> warnings;

        private Result(Manifest manifest, List<String> warnings) {
            this.manifest = manifest;
            this.warnings = Collections.unmodifiableList(warnings);
        }

        public Manifest getManifest() {
            return manifest;
        }

        public List<String> getWarnings() {
            return warnings;
        }
    }

    public ManifestBuilder applicationName(String name) {
        this.applicationName = name;
        return this;
    }

    public ManifestBuilder contextPath(String path) {
        this.contextPath = path;
        return this;
    }

    public ManifestBuilder servletPath(String path) {
        this.servletPath = path;
        return this;
    }

    public ManifestBuilder excludePackages(Collection<String> packages) {
        excludedPackages.addAll(packages);
        return this;
    }

    public ManifestBuilder authentication(Collection<String> schemes) {
        authenticationSchemes.addAll(schemes);
        return this;
    }

    public Result build(Collection<MappingDescription> mappings) {
        Set<String> warnings = new LinkedHashSet<>();
        Map<String, Endpoint> endpoints = new LinkedHashMap<>();
        SchemaMapper schemas = new SchemaMapper(warnings);
        List<MappingDescription> ordered = new ArrayList<>(mappings);
        ordered.sort(Comparator.comparing(ManifestBuilder::orderKey, CodePoints.ORDER));
        for (MappingDescription mapping : ordered) {
            if (isExcluded(mapping.getHandler().getBeanType())) {
                continue;
            }
            try {
                for (Endpoint endpoint : endpoints(mapping, warnings, schemas)) {
                    if (endpoints.putIfAbsent(endpoint.getId(), endpoint) != null) {
                        warnings.add("Duplicate endpoint skipped: " + endpoint.getId());
                    }
                }
            } catch (RuntimeException | LinkageError error) {
                warnings.add("Skipped " + mapping.getHandler().getShortLogMessage() + ": " + error);
            }
        }
        List<Endpoint> sorted = new ArrayList<>(endpoints.values());
        sorted.sort((a, b) -> {
            int result = CodePoints.compare(a.getPath(), b.getPath());
            if (result == 0) {
                result = CodePoints.compare(a.getMethod(), b.getMethod());
            }
            return result != 0 ? result : CodePoints.compare(a.getId(), b.getId());
        });
        Manifest manifest = new Manifest()
                .setManifestVersion(ManifestVersion.CURRENT)
                .setSource(ManifestSource.RUNTIME)
                .setGenerator(new Generator()
                        .setName("apiro-spring-webmvc")
                        .setVersion(ManifestBuilder.class.getPackage().getImplementationVersion())
                        .setFramework("spring-webmvc")
                        .setFrameworkVersion(SpringVersion.getVersion()))
                .setGeneratedAt(Instant.now().truncatedTo(ChronoUnit.SECONDS).toString())
                .setEndpoints(sorted)
                .setSchemas(schemas.schemas());
        if (applicationName != null || contextPath != null || servletPath != null) {
            manifest.setApplication(new Application().setName(applicationName).setContextPath(contextPath)
                    .setServletPath(servletPath));
        }
        return new Result(manifest, new ArrayList<>(warnings));
    }

    private static String orderKey(MappingDescription mapping) {
        return mapping.getHandler().getBeanType().getName() + " " + mapping.getHandler().getMethod() + " "
                + mapping.getPatterns() + " " + mapping.getMethods();
    }

    private boolean isExcluded(Class<?> type) {
        String name = type.getName();
        if (type == ApiroManifestController.class
                || name.startsWith("org.springframework.boot.") && name.endsWith(".BasicErrorController")) {
            return true;
        }
        for (String excluded : excludedPackages) {
            if (name.startsWith(excluded + ".")) {
                return true;
            }
        }
        return false;
    }

    private List<Endpoint> endpoints(MappingDescription mapping, Set<String> warnings, SchemaMapper schemas) {
        HandlerMethod handler = mapping.getHandler();
        Class<?> type = handler.getBeanType();
        Method method = handler.getMethod();
        List<String> parameterTypes = new ArrayList<>();
        for (Class<?> parameterType : method.getParameterTypes()) {
            parameterTypes.add(parameterType.getTypeName());
        }
        String signature = type.getName() + "." + method.getName() + "(" + String.join(",", parameterTypes) + ")";
        Set<String> methods = mapping.getMethods().isEmpty()
                ? Collections.singleton(Endpoint.ANY_METHOD) : mapping.getMethods();
        List<Endpoint> result = new ArrayList<>();
        for (String declared : mapping.getPatterns()) {
            String pattern = declared.isEmpty() ? "/" : declared;
            PathTemplate template = PathTemplate.parse(pattern);
            ParameterReader reader = new ParameterReader(template, mapping.getConsumes(),
                    handler.getShortLogMessage(), warnings, schemas);
            reader.read(handler.getMethodParameters());
            for (String verb : methods) {
                result.add(new Endpoint()
                        .setId(verb + " " + pattern + "#" + signature)
                        .setMethod(verb)
                        .setPath(template.path())
                        .setPattern(pattern.equals(template.path()) ? null : pattern)
                        .setGroup(type.getSimpleName())
                        .setName(method.getName())
                        .setDeprecated(type.isAnnotationPresent(Deprecated.class)
                                || method.isAnnotationPresent(Deprecated.class))
                        .setConsumes(new ArrayList<>(mapping.getConsumes()))
                        .setProduces(new ArrayList<>(mapping.getProduces()))
                        .setConditions(conditions(mapping))
                        .setParameters(reader.parameters())
                        .setBody(reader.body())
                        .setSecurity(SecurityReader.read(type, method, authenticationSchemes))
                        .setHandler(new Handler()
                                .setClassName(type.getName())
                                .setMethodName(method.getName())
                                .setParameterTypes(parameterTypes)));
            }
        }
        return result;
    }

    private static Conditions conditions(MappingDescription mapping) {
        if (mapping.getParams().isEmpty() && mapping.getHeaders().isEmpty() && mapping.getUnsupported().isEmpty()) {
            return null;
        }
        return new Conditions()
                .setParams(new ArrayList<>(mapping.getParams()))
                .setHeaders(new ArrayList<>(mapping.getHeaders()))
                .setUnsupported(new ArrayList<>(mapping.getUnsupported()));
    }
}
