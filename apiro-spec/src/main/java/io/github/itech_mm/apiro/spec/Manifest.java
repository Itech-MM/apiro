package io.github.itech_mm.apiro.spec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class Manifest {
    private int manifestVersion;
    private String source;
    private Generator generator;
    private String generatedAt;
    private Application application;
    private List<Endpoint> endpoints = new ArrayList<>();
    private Map<String, Schema> schemas = new LinkedHashMap<>();

    public int getManifestVersion() {
        return manifestVersion;
    }

    public Manifest setManifestVersion(int manifestVersion) {
        this.manifestVersion = manifestVersion;
        return this;
    }

    public String getSource() {
        return source;
    }

    public Manifest setSource(String source) {
        this.source = source;
        return this;
    }

    public Generator getGenerator() {
        return generator;
    }

    public Manifest setGenerator(Generator generator) {
        this.generator = generator;
        return this;
    }

    public String getGeneratedAt() {
        return generatedAt;
    }

    public Manifest setGeneratedAt(String generatedAt) {
        this.generatedAt = generatedAt;
        return this;
    }

    public Application getApplication() {
        return application;
    }

    public Manifest setApplication(Application application) {
        this.application = application;
        return this;
    }

    public List<Endpoint> getEndpoints() {
        return endpoints;
    }

    public Manifest setEndpoints(List<Endpoint> endpoints) {
        this.endpoints = endpoints == null ? new ArrayList<>() : new ArrayList<>(endpoints);
        return this;
    }

    public Map<String, Schema> getSchemas() {
        return schemas;
    }

    public Manifest setSchemas(Map<String, Schema> schemas) {
        this.schemas = schemas == null ? new LinkedHashMap<>() : new LinkedHashMap<>(schemas);
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Manifest)) {
            return false;
        }
        Manifest other = (Manifest) o;
        return manifestVersion == other.manifestVersion
                && Objects.equals(source, other.source)
                && Objects.equals(generator, other.generator)
                && Objects.equals(generatedAt, other.generatedAt)
                && Objects.equals(application, other.application)
                && Objects.equals(endpoints, other.endpoints)
                && Objects.equals(schemas, other.schemas);
    }

    @Override
    public int hashCode() {
        return Objects.hash(manifestVersion, source, generator, generatedAt, application, endpoints, schemas);
    }

    @Override
    public String toString() {
        return "Manifest{" +
                "manifestVersion=" + manifestVersion +
                ", source=" + source +
                ", generator=" + generator +
                ", generatedAt=" + generatedAt +
                ", application=" + application +
                ", endpoints=" + endpoints +
                ", schemas=" + schemas +
                "}";
    }
}
