package io.github.itech_mm.apiro.spec;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * One HTTP method on one path.
 *
 * <p>Plain bean: no JSON library, no framework types. Collections are never null.
 * Absent optional values are null (or false / empty).
 */
public final class Endpoint {

    private String id;
    private String method;
    private String path;
    private String group;
    private String name;
    private String summary;
    private boolean deprecated;
    private List<String> consumes = new ArrayList<>();
    private List<String> produces = new ArrayList<>();
    private List<Parameter> parameters = new ArrayList<>();
    private Body body;
    private Security security;
    private Handler handler;

    public String getId() {
        return id;
    }

    public Endpoint setId(String id) {
        this.id = id;
        return this;
    }

    public String getMethod() {
        return method;
    }

    public Endpoint setMethod(String method) {
        this.method = method;
        return this;
    }

    public String getPath() {
        return path;
    }

    public Endpoint setPath(String path) {
        this.path = path;
        return this;
    }

    public String getGroup() {
        return group;
    }

    public Endpoint setGroup(String group) {
        this.group = group;
        return this;
    }

    public String getName() {
        return name;
    }

    public Endpoint setName(String name) {
        this.name = name;
        return this;
    }

    public String getSummary() {
        return summary;
    }

    public Endpoint setSummary(String summary) {
        this.summary = summary;
        return this;
    }

    public boolean isDeprecated() {
        return deprecated;
    }

    public Endpoint setDeprecated(boolean deprecated) {
        this.deprecated = deprecated;
        return this;
    }

    public List<String> getConsumes() {
        return consumes;
    }

    public Endpoint setConsumes(List<String> consumes) {
        this.consumes = consumes == null ? new ArrayList<>() : new ArrayList<>(consumes);
        return this;
    }

    public List<String> getProduces() {
        return produces;
    }

    public Endpoint setProduces(List<String> produces) {
        this.produces = produces == null ? new ArrayList<>() : new ArrayList<>(produces);
        return this;
    }

    public List<Parameter> getParameters() {
        return parameters;
    }

    public Endpoint setParameters(List<Parameter> parameters) {
        this.parameters = parameters == null ? new ArrayList<>() : new ArrayList<>(parameters);
        return this;
    }

    public Body getBody() {
        return body;
    }

    public Endpoint setBody(Body body) {
        this.body = body;
        return this;
    }

    public Security getSecurity() {
        return security;
    }

    public Endpoint setSecurity(Security security) {
        this.security = security;
        return this;
    }

    public Handler getHandler() {
        return handler;
    }

    public Endpoint setHandler(Handler handler) {
        this.handler = handler;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Endpoint)) {
            return false;
        }
        Endpoint other = (Endpoint) o;
        return Objects.equals(id, other.id)
                && Objects.equals(method, other.method)
                && Objects.equals(path, other.path)
                && Objects.equals(group, other.group)
                && Objects.equals(name, other.name)
                && Objects.equals(summary, other.summary)
                && deprecated == other.deprecated
                && Objects.equals(consumes, other.consumes)
                && Objects.equals(produces, other.produces)
                && Objects.equals(parameters, other.parameters)
                && Objects.equals(body, other.body)
                && Objects.equals(security, other.security)
                && Objects.equals(handler, other.handler);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, method, path, group, name, summary, deprecated, consumes, produces, parameters, body, security, handler);
    }

    @Override
    public String toString() {
        return "Endpoint{" +
                "id=" + id +
                ", method=" + method +
                ", path=" + path +
                ", group=" + group +
                ", name=" + name +
                ", summary=" + summary +
                ", deprecated=" + deprecated +
                ", consumes=" + consumes +
                ", produces=" + produces +
                ", parameters=" + parameters +
                ", body=" + body +
                ", security=" + security +
                ", handler=" + handler +
                "}";
    }
}
