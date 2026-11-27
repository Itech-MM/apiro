package io.github.itech_mm.apiro.spec;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class AccessLevel {
    public static final String RESTRICTED = "restricted";
    public static final String OPEN = "open";

    public static final Set<String> ALL = Collections.unmodifiableSet(
            new LinkedHashSet<>(Arrays.asList(RESTRICTED, OPEN)));

    private AccessLevel() {
    }
}
