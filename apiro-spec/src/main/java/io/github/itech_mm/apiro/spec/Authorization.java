package io.github.itech_mm.apiro.spec;

import java.util.Objects;

public final class Authorization {
    private String status;
    private String access;
    private String source;

    public String getStatus() {
        return status;
    }

    public Authorization setStatus(String status) {
        this.status = status;
        return this;
    }

    public String getAccess() {
        return access;
    }

    public Authorization setAccess(String access) {
        this.access = access;
        return this;
    }

    public String getSource() {
        return source;
    }

    public Authorization setSource(String source) {
        this.source = source;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Authorization)) {
            return false;
        }
        Authorization other = (Authorization) o;
        return Objects.equals(status, other.status)
                && Objects.equals(access, other.access)
                && Objects.equals(source, other.source);
    }

    @Override
    public int hashCode() {
        return Objects.hash(status, access, source);
    }

    @Override
    public String toString() {
        return "Authorization{status=" + status +
                ", access=" + access +
                ", source=" + source +
                "}";
    }
}
