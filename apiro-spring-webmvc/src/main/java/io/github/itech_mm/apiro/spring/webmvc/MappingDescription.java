package io.github.itech_mm.apiro.spring.webmvc;

import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.web.method.HandlerMethod;

public final class MappingDescription {
    private final HandlerMethod handler;
    private final Set<String> patterns = new TreeSet<>();
    private final Set<String> methods = new TreeSet<>();
    private final Set<String> consumes = new TreeSet<>(CodePoints.ORDER);
    private final Set<String> produces = new TreeSet<>(CodePoints.ORDER);
    private final Set<String> params = new TreeSet<>(CodePoints.ORDER);
    private final Set<String> headers = new TreeSet<>(CodePoints.ORDER);
    private final Set<String> unsupported = new TreeSet<>(CodePoints.ORDER);

    public MappingDescription(HandlerMethod handler) {
        this.handler = handler;
    }

    public HandlerMethod getHandler() {
        return handler;
    }

    public Set<String> getPatterns() {
        return patterns;
    }

    public Set<String> getMethods() {
        return methods;
    }

    public Set<String> getConsumes() {
        return consumes;
    }

    public Set<String> getProduces() {
        return produces;
    }

    public Set<String> getParams() {
        return params;
    }

    public Set<String> getHeaders() {
        return headers;
    }

    public Set<String> getUnsupported() {
        return unsupported;
    }

    public MappingDescription patterns(String... values) {
        patterns.addAll(Arrays.asList(values));
        return this;
    }

    public MappingDescription methods(String... values) {
        methods.addAll(Arrays.asList(values));
        return this;
    }

    public MappingDescription consumes(String... values) {
        consumes.addAll(Arrays.asList(values));
        return this;
    }
}
