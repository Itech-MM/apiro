package io.github.itech_mm.apiro.spring.webmvc;

import io.github.itech_mm.apiro.spec.ManifestValidator;
import io.github.itech_mm.apiro.spec.Schema;
import io.github.itech_mm.apiro.spec.SchemaType;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.core.ResolvableType;

final class SchemaMapper {
    private static final int MAX_DEPTH = 5;
    private static final int MAX_CONTAINERS = 10;
    private static final String[] OPAQUE = {"java.", "javax.", "jakarta.", "org.springframework.",
            "com.fasterxml.jackson.", "tools.jackson."};
    private static final String JSON_PROPERTY = "com.fasterxml.jackson.annotation.JsonProperty";
    private static final String JSON_IGNORE = "com.fasterxml.jackson.annotation.JsonIgnore";

    private final Map<String, Schema> named = new LinkedHashMap<>();
    private final Set<String> warnings;

    SchemaMapper(Set<String> warnings) {
        this.warnings = warnings;
    }

    Map<String, Schema> schemas() {
        return named;
    }

    Schema map(ResolvableType type) {
        return map(type, 0, 0);
    }

    private Schema map(ResolvableType type, int depth, int containers) {
        Class<?> raw = type.resolve();
        if (raw == null || containers > MAX_CONTAINERS) {
            return any();
        }
        if (raw == Optional.class) {
            return map(type.getGeneric(0), depth, containers + 1);
        }
        Schema simple = SimpleSchemas.of(type, SchemaType.ANY);
        boolean array = raw != byte[].class && (raw.isArray() || Collection.class.isAssignableFrom(raw));
        if (array) {
            ResolvableType element = raw.isArray() ? type.getComponentType() : type.asCollection().getGeneric(0);
            return new Schema().setType(SchemaType.ARRAY).setItems(map(element, depth, containers + 1));
        }
        if (!SchemaType.ANY.equals(simple.getType())) {
            return simple;
        }
        if (Map.class.isAssignableFrom(raw)) {
            return new Schema().setType(SchemaType.OBJECT);
        }
        if (raw.isInterface() || raw.isPrimitive() || isOpaque(raw)) {
            return any();
        }
        return reference(type, raw, depth);
    }

    private Schema reference(ResolvableType type, Class<?> raw, int depth) {
        String key = name(type, false, 0);
        if (!named.containsKey(key)) {
            if (depth >= MAX_DEPTH) {
                warnings.add("Schema " + key + " is nested deeper than " + MAX_DEPTH + " levels, written as any");
                return any();
            }
            if (named.size() >= ManifestValidator.MAX_SCHEMAS) {
                warnings.add("More than " + ManifestValidator.MAX_SCHEMAS + " schemas, " + key + " written as any");
                return any();
            }
            Schema schema = new Schema().setType(SchemaType.OBJECT).setTitle(name(type, true, 0));
            named.put(key, schema);
            fill(schema, type, raw, depth + 1);
        }
        return new Schema().setRef(key);
    }

    private void fill(Schema schema, ResolvableType type, Class<?> raw, int depth) {
        List<Class<?>> chain = new ArrayList<>();
        for (Class<?> each = raw; each != null && !isOpaque(each); each = each.getSuperclass()) {
            chain.add(0, each);
        }
        List<String> required = new ArrayList<>();
        for (Class<?> declaring : chain) {
            ResolvableType owner = declaring == raw ? type : type.as(declaring);
            for (Field field : declaring.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())
                        || field.isSynthetic()) {
                    continue;
                }
                List<AnnotatedElement> elements = elements(raw, field);
                Annotation ignore = find(elements, JSON_IGNORE);
                Annotation rename = find(elements, JSON_PROPERTY);
                if (ignore != null && !Boolean.FALSE.equals(Annotations.value(ignore, "value"))) {
                    continue;
                }
                if (rename == null && elements.size() == 1 && !Modifier.isPublic(field.getModifiers())) {
                    continue;
                }
                Object renamed = rename == null ? null : Annotations.value(rename, "value");
                String name = renamed == null || renamed.toString().isEmpty() ? field.getName() : renamed.toString();
                Schema property = map(resolve(field.getGenericType(), owner), depth, 0);
                boolean mandatory = false;
                for (AnnotatedElement element : elements) {
                    mandatory |= Constraints.apply(element.getAnnotations(), property);
                }
                schema.getProperties().put(name, property);
                if (mandatory && !required.contains(name)) {
                    required.add(name);
                }
            }
        }
        schema.setRequired(required);
    }

    private static List<AnnotatedElement> elements(Class<?> type, Field field) {
        List<AnnotatedElement> result = new ArrayList<>();
        result.add(field);
        String name = field.getName();
        String capital = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        List<String> accessors = Arrays.asList(name, "get" + capital, "is" + capital);
        for (Class<?> each = type; each != null && !isOpaque(each); each = each.getSuperclass()) {
            for (Method method : each.getDeclaredMethods()) {
                if (Modifier.isStatic(method.getModifiers()) || method.isBridge()) {
                    continue;
                }
                boolean getter = method.getParameterCount() == 0 && accessors.contains(method.getName());
                boolean setter = method.getParameterCount() == 1 && method.getName().equals("set" + capital);
                if (getter || setter) {
                    result.add(method);
                }
            }
        }
        return result;
    }

    private static Annotation find(List<AnnotatedElement> elements, String name) {
        for (AnnotatedElement element : elements) {
            for (Annotation annotation : element.getAnnotations()) {
                if (annotation.annotationType().getName().equals(name)) {
                    return annotation;
                }
            }
        }
        return null;
    }

    private static ResolvableType resolve(Type type, ResolvableType owner) {
        if (type instanceof Class) {
            return ResolvableType.forClass((Class<?>) type);
        }
        if (type instanceof TypeVariable) {
            TypeVariable<?>[] variables = owner.resolve(Object.class).getTypeParameters();
            for (int i = 0; i < variables.length; i++) {
                if (variables[i].equals(type) && owner.getGeneric(i).resolve() != null) {
                    return owner.getGeneric(i);
                }
            }
        } else if (type instanceof ParameterizedType) {
            ParameterizedType parameterized = (ParameterizedType) type;
            Type[] arguments = parameterized.getActualTypeArguments();
            ResolvableType[] resolved = new ResolvableType[arguments.length];
            for (int i = 0; i < arguments.length; i++) {
                resolved[i] = resolve(arguments[i], owner);
            }
            return ResolvableType.forClassWithGenerics((Class<?>) parameterized.getRawType(), resolved);
        } else if (type instanceof GenericArrayType) {
            return ResolvableType.forArrayComponent(
                    resolve(((GenericArrayType) type).getGenericComponentType(), owner));
        } else if (type instanceof WildcardType) {
            return resolve(((WildcardType) type).getUpperBounds()[0], owner);
        }
        return ResolvableType.forClass(Object.class);
    }

    private static String name(ResolvableType type, boolean simple, int level) {
        Class<?> raw = type.resolve(Object.class);
        if (raw.isArray()) {
            return name(type.getComponentType(), simple, level + 1) + "[]";
        }
        String base = simple ? raw.getSimpleName() : raw.getName();
        ResolvableType[] generics = type.getGenerics();
        if (generics.length == 0 || level >= MAX_DEPTH) {
            return base;
        }
        List<String> names = new ArrayList<>();
        for (ResolvableType generic : generics) {
            names.add(name(generic, simple, level + 1));
        }
        return base + "<" + String.join(", ", names) + ">";
    }

    private static boolean isOpaque(Class<?> type) {
        for (String prefix : OPAQUE) {
            if (type.getName().startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static Schema any() {
        return new Schema().setType(SchemaType.ANY);
    }
}
