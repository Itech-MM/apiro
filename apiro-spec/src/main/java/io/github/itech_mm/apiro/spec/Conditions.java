package io.github.itech_mm.apiro.spec;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class Conditions {
    private List<String> params = new ArrayList<>();
    private List<String> headers = new ArrayList<>();
    private List<String> unsupported = new ArrayList<>();

    public List<String> getParams() {
        return params;
    }

    public Conditions setParams(List<String> params) {
        this.params = params == null ? new ArrayList<>() : new ArrayList<>(params);
        return this;
    }

    public List<String> getHeaders() {
        return headers;
    }

    public Conditions setHeaders(List<String> headers) {
        this.headers = headers == null ? new ArrayList<>() : new ArrayList<>(headers);
        return this;
    }

    public List<String> getUnsupported() {
        return unsupported;
    }

    public Conditions setUnsupported(List<String> unsupported) {
        this.unsupported = unsupported == null ? new ArrayList<>() : new ArrayList<>(unsupported);
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Conditions)) {
            return false;
        }
        Conditions other = (Conditions) o;
        return Objects.equals(params, other.params)
                && Objects.equals(headers, other.headers)
                && Objects.equals(unsupported, other.unsupported);
    }

    @Override
    public int hashCode() {
        return Objects.hash(params, headers, unsupported);
    }

    @Override
    public String toString() {
        return "Conditions{params=" + params +
                ", headers=" + headers +
                ", unsupported=" + unsupported +
                "}";
    }
}
