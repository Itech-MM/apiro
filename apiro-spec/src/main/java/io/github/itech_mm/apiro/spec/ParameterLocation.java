package io.github.itech_mm.apiro.spec;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class ParameterLocation {
    public static final String PATH = "path";
    public static final String QUERY = "query";
    public static final String HEADER = "header";
    public static final String COOKIE = "cookie";

    public static final String FORM = "form";

    public static final String PART = "part";

    public static final Set<String> ALL = Collections.unmodifiableSet(
            new LinkedHashSet<>(Arrays.asList(PATH, QUERY, HEADER, COOKIE, FORM, PART)));

    private ParameterLocation() {
    }
}
