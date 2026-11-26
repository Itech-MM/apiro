package io.github.itech_mm.apiro.spec;

import java.util.Objects;

public final class Generator {
    private String name;
    private String version;
    private String framework;
    private String frameworkVersion;

    public String getName() {
        return name;
    }

    public Generator setName(String name) {
        this.name = name;
        return this;
    }

    public String getVersion() {
        return version;
    }

    public Generator setVersion(String version) {
        this.version = version;
        return this;
    }

    public String getFramework() {
        return framework;
    }

    public Generator setFramework(String framework) {
        this.framework = framework;
        return this;
    }

    public String getFrameworkVersion() {
        return frameworkVersion;
    }

    public Generator setFrameworkVersion(String frameworkVersion) {
        this.frameworkVersion = frameworkVersion;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Generator)) {
            return false;
        }
        Generator other = (Generator) o;
        return Objects.equals(name, other.name)
                && Objects.equals(version, other.version)
                && Objects.equals(framework, other.framework)
                && Objects.equals(frameworkVersion, other.frameworkVersion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, version, framework, frameworkVersion);
    }

    @Override
    public String toString() {
        return "Generator{" +
                "name=" + name +
                ", version=" + version +
                ", framework=" + framework +
                ", frameworkVersion=" + frameworkVersion +
                "}";
    }
}
