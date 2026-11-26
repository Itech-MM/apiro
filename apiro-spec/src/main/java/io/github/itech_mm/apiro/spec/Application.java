package io.github.itech_mm.apiro.spec;

import java.util.Objects;

public final class Application {
    private String name;
    private String contextPath;
    private String servletPath;

    public String getName() {
        return name;
    }

    public Application setName(String name) {
        this.name = name;
        return this;
    }

    public String getContextPath() {
        return contextPath;
    }

    public Application setContextPath(String contextPath) {
        this.contextPath = contextPath;
        return this;
    }

    public String getServletPath() {
        return servletPath;
    }

    public Application setServletPath(String servletPath) {
        this.servletPath = servletPath;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Application)) {
            return false;
        }
        Application other = (Application) o;
        return Objects.equals(name, other.name)
                && Objects.equals(contextPath, other.contextPath)
                && Objects.equals(servletPath, other.servletPath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, contextPath, servletPath);
    }

    @Override
    public String toString() {
        return "Application{" +
                "name=" + name +
                ", contextPath=" + contextPath +
                ", servletPath=" + servletPath +
                "}";
    }
}
