package io.github.itech_mm.apiro.spec;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class Security {
    private Boolean required;
    private List<String> schemes = new ArrayList<>();

    public Boolean getRequired() {
        return required;
    }

    public Security setRequired(Boolean required) {
        this.required = required;
        return this;
    }

    public List<String> getSchemes() {
        return schemes;
    }

    public Security setSchemes(List<String> schemes) {
        this.schemes = schemes == null ? new ArrayList<>() : new ArrayList<>(schemes);
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Security)) {
            return false;
        }
        Security other = (Security) o;
        return Objects.equals(required, other.required)
                && Objects.equals(schemes, other.schemes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(required, schemes);
    }

    @Override
    public String toString() {
        return "Security{" +
                "required=" + required +
                ", schemes=" + schemes +
                "}";
    }
}
