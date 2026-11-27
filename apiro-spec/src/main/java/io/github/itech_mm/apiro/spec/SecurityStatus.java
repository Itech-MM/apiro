package io.github.itech_mm.apiro.spec;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class SecurityStatus {
    public static final String UNKNOWN = "unknown";
    public static final String DECLARED = "declared";

    public static final Set<String> ALL = Collections.unmodifiableSet(
            new LinkedHashSet<>(Arrays.asList(UNKNOWN, DECLARED)));

    private SecurityStatus() {
    }
}
