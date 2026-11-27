package io.github.itech_mm.apiro.spec;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class Authentication {
    private String status;
    private List<String> schemes = new ArrayList<>();
    private String source;

    public String getStatus() {
        return status;
    }

    public Authentication setStatus(String status) {
        this.status = status;
        return this;
    }

    public List<String> getSchemes() {
        return schemes;
    }

    public Authentication setSchemes(List<String> schemes) {
        this.schemes = schemes == null ? new ArrayList<>() : new ArrayList<>(schemes);
        return this;
    }

    public String getSource() {
        return source;
    }

    public Authentication setSource(String source) {
        this.source = source;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Authentication)) {
            return false;
        }
        Authentication other = (Authentication) o;
        return Objects.equals(status, other.status)
                && Objects.equals(schemes, other.schemes)
                && Objects.equals(source, other.source);
    }

    @Override
    public int hashCode() {
        return Objects.hash(status, schemes, source);
    }

    @Override
    public String toString() {
        return "Authentication{status=" + status +
                ", schemes=" + schemes +
                ", source=" + source +
                "}";
    }
}
