package io.github.itech_mm.apiro.spec;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Allowed values of {@link Parameter#getIn()}. */
public final class ParameterLocation {

    public static final String PATH = "path";
    public static final String QUERY = "query";
    public static final String HEADER = "header";
    public static final String COOKIE = "cookie";
    /** A field of an application/x-www-form-urlencoded body. */
    public static final String FORM = "form";
    /** A part of a multipart body. */
    public static final String PART = "part";

    public static final Set<String> ALL = Collections.unmodifiableSet(
            new LinkedHashSet<>(Arrays.asList(PATH, QUERY, HEADER, COOKIE, FORM, PART)));

    private ParameterLocation() {
    }
}
