package io.github.itech_mm.apiro.spring.webmvc;

import io.github.itech_mm.apiro.spec.Schema;
import io.github.itech_mm.apiro.spec.SchemaType;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.net.URL;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.core.ResolvableType;
import org.springframework.web.multipart.MultipartFile;

final class SimpleSchemas {
    private static final int MAX_DEPTH = 5;
    private static final Map<Class<?>, String[]> TYPES = new HashMap<>();

    static {
        add(SchemaType.BOOLEAN, null, boolean.class, Boolean.class);
        add(SchemaType.INTEGER, "int32", int.class, Integer.class, short.class, Short.class, byte.class, Byte.class);
        add(SchemaType.INTEGER, "int64", long.class, Long.class);
        add(SchemaType.INTEGER, null, BigInteger.class);
        add(SchemaType.NUMBER, "float", float.class, Float.class);
        add(SchemaType.NUMBER, "double", double.class, Double.class);
        add(SchemaType.NUMBER, null, BigDecimal.class, Number.class);
        add(SchemaType.STRING, null, String.class, CharSequence.class, char.class, Character.class);
        add(SchemaType.STRING, "uuid", UUID.class);
        add(SchemaType.STRING, "uri", URI.class, URL.class);
        add(SchemaType.STRING, "date", LocalDate.class);
        add(SchemaType.STRING, "date-time", LocalDateTime.class, OffsetDateTime.class, ZonedDateTime.class,
                Instant.class, Date.class);
        add(SchemaType.STRING, "time", LocalTime.class, OffsetTime.class);
        add(SchemaType.STRING, "byte", byte[].class);
    }

    private SimpleSchemas() {
    }

    private static void add(String type, String format, Class<?>... classes) {
        for (Class<?> each : classes) {
            TYPES.put(each, new String[] {type, format});
        }
    }

    static Schema of(ResolvableType type, String fallback) {
        return of(type, fallback, 0);
    }

    private static Schema of(ResolvableType type, String fallback, int depth) {
        Class<?> raw = type.resolve();
        if (raw == null || depth >= MAX_DEPTH) {
            return new Schema().setType(SchemaType.ANY);
        }
        String[] known = TYPES.get(raw);
        if (known != null) {
            return new Schema().setType(known[0]).setFormat(known[1]);
        }
        if (isFile(raw)) {
            return new Schema().setType(SchemaType.FILE);
        }
        if (raw.isEnum()) {
            List<String> values = new ArrayList<>();
            for (Object constant : raw.getEnumConstants()) {
                values.add(((Enum<?>) constant).name());
            }
            return new Schema().setType(SchemaType.STRING).setEnumValues(values);
        }
        if (raw.isArray() || Collection.class.isAssignableFrom(raw)) {
            ResolvableType element = raw.isArray() ? type.getComponentType() : type.asCollection().getGeneric(0);
            return new Schema().setType(SchemaType.ARRAY).setItems(of(element, fallback, depth + 1));
        }
        return new Schema().setType(fallback);
    }

    static boolean isFile(Class<?> type) {
        String name = type.getName();
        return MultipartFile.class.isAssignableFrom(type) || name.equals("javax.servlet.http.Part")
                || name.equals("jakarta.servlet.http.Part");
    }
}
