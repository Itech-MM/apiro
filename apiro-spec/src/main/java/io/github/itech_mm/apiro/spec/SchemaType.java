package io.github.itech_mm.apiro.spec;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class SchemaType {
    public static final String STRING = "string";
    public static final String INTEGER = "integer";
    public static final String NUMBER = "number";
    public static final String BOOLEAN = "boolean";
    public static final String ARRAY = "array";
    public static final String OBJECT = "object";

    public static final String FILE = "file";

    public static final String ANY = "any";

    public static final Set<String> ALL = Collections.unmodifiableSet(
            new LinkedHashSet<>(Arrays.asList(STRING, INTEGER, NUMBER, BOOLEAN, ARRAY, OBJECT, FILE, ANY)));

    private SchemaType() {
    }
}
