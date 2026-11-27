package io.github.itech_mm.apiro.spec;

import java.util.Objects;

public final class Security {
    private Authentication authentication;
    private Authorization authorization;

    public Authentication getAuthentication() {
        return authentication;
    }

    public Security setAuthentication(Authentication authentication) {
        this.authentication = authentication;
        return this;
    }

    public Authorization getAuthorization() {
        return authorization;
    }

    public Security setAuthorization(Authorization authorization) {
        this.authorization = authorization;
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
        return Objects.equals(authentication, other.authentication)
                && Objects.equals(authorization, other.authorization);
    }

    @Override
    public int hashCode() {
        return Objects.hash(authentication, authorization);
    }

    @Override
    public String toString() {
        return "Security{authentication=" + authentication +
                ", authorization=" + authorization +
                "}";
    }
}
