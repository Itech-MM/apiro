package io.github.itech_mm.apiro.spec;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class ManifestSource {
    public static final String RUNTIME = "runtime";
    public static final String BUILD = "build";
    public static final String OPENAPI = "openapi";

    public static final Set<String> ALL = Collections.unmodifiableSet(
            new LinkedHashSet<>(Arrays.asList(RUNTIME, BUILD, OPENAPI)));

    private ManifestSource() {
    }
}
