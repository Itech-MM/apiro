package io.github.itech_mm.apiro.service.manifest;

import io.github.itech_mm.apiro.spec.Schema;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public final class SchemaFingerprint {

    private static final int MAX_DEPTH = 5;
    private static final int HASH_BYTES = 8;

    private final Map<String, Schema> named;
    private final Set<String> active = new HashSet<>();

    private SchemaFingerprint(Map<String, Schema> named) {
        this.named = named == null ? Map.of() : named;
    }

    public static String of(Schema schema, Map<String, Schema> named) {
        if (schema == null) {
            return null;
        }
        StringBuilder text = new StringBuilder();
        new SchemaFingerprint(named).append(text, schema, 0);
        return hash(text.toString());
    }

    private void append(StringBuilder out, Schema schema, int depth) {
        if (schema == null) {
            out.append('-');
            return;
        }
        if (schema.getRef() != null) {
            String name = schema.getRef();
            Schema target = named.get(name);
            if (target == null) {
                out.append('?');
                field(out, name);
                return;
            }
            if (depth > MAX_DEPTH || !active.add(name)) {
                out.append('@');
                field(out, name);
                return;
            }
            try {
                append(out, target, depth + 1);
            } finally {
                active.remove(name);
            }
            return;
        }
        out.append('{');
        field(out, schema.getType());
        field(out, schema.getFormat());
        field(out, schema.getNullable());
        field(out, schema.getMinimum());
        field(out, schema.getMaximum());
        field(out, schema.getMinLength());
        field(out, schema.getMaxLength());
        field(out, schema.getPattern());
        field(out, schema.getMinItems());
        field(out, schema.getMaxItems());
        list(out, schema.getEnumValues());
        list(out, new TreeSet<>(schema.getRequired()));
        append(out, schema.getItems(), depth + 1);
        for (Map.Entry<String, Schema> property : new TreeMap<>(schema.getProperties()).entrySet()) {
            field(out, property.getKey());
            append(out, property.getValue(), depth + 1);
        }
        out.append('}');
    }

    private static void list(StringBuilder out, Iterable<String> values) {
        out.append('[');
        for (String value : values) {
            field(out, value);
        }
        out.append(']');
    }

    private static void field(StringBuilder out, Object value) {
        if (value == null) {
            out.append("~|");
            return;
        }
        String text = String.valueOf(value);
        out.append(text.length()).append(':').append(text).append('|');
    }

    private static String hash(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < HASH_BYTES; i++) {
                hex.append(String.format("%02x", digest[i]));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }
}
