package io.github.itech_mm.apiro.spring.webmvc;

import io.github.itech_mm.apiro.spec.Schema;
import io.github.itech_mm.apiro.spec.SchemaType;
import java.lang.annotation.Annotation;

final class Constraints {
    private static final String[] PACKAGES = {"javax.validation.constraints.", "jakarta.validation.constraints."};

    private Constraints() {
    }

    static boolean apply(Annotation[] annotations, Schema schema) {
        boolean required = false;
        for (Annotation annotation : annotations) {
            String name = name(annotation);
            if (name == null || grouped(annotation)) {
                continue;
            }
            boolean text = SchemaType.STRING.equals(schema.getType());
            boolean array = SchemaType.ARRAY.equals(schema.getType());
            boolean integer = SchemaType.INTEGER.equals(schema.getType());
            boolean numeric = integer || SchemaType.NUMBER.equals(schema.getType());
            switch (name) {
                case "NotNull":
                    required = true;
                    break;
                case "NotBlank":
                case "NotEmpty":
                    required = true;
                    if (text) {
                        minLength(schema, 1);
                    } else if (array && name.equals("NotEmpty")) {
                        minItems(schema, 1);
                    }
                    break;
                case "Size":
                    size(annotation, schema, text, array);
                    break;
                case "Min":
                case "DecimalMin":
                    if (numeric && inclusive(annotation)) {
                        minimum(schema, number(annotation));
                    }
                    break;
                case "Max":
                case "DecimalMax":
                    if (numeric && inclusive(annotation)) {
                        maximum(schema, number(annotation));
                    }
                    break;
                case "Positive":
                    if (integer) {
                        minimum(schema, 1);
                    }
                    break;
                case "PositiveOrZero":
                    if (integer) {
                        minimum(schema, 0);
                    }
                    break;
                case "Pattern":
                    Object regexp = Annotations.value(annotation, "regexp");
                    if (text && regexp != null) {
                        schema.setPattern(regexp.toString());
                    }
                    break;
                case "Email":
                    if (text) {
                        schema.setFormat("email");
                    }
                    break;
                default:
                    break;
            }
        }
        return required;
    }

    private static String name(Annotation annotation) {
        String full = annotation.annotationType().getName();
        for (String prefix : PACKAGES) {
            if (full.startsWith(prefix)) {
                return full.substring(prefix.length());
            }
        }
        return null;
    }

    private static boolean grouped(Annotation annotation) {
        Object groups = Annotations.value(annotation, "groups");
        return groups instanceof Class[] && ((Class<?>[]) groups).length > 0;
    }

    private static boolean inclusive(Annotation annotation) {
        return !Boolean.FALSE.equals(Annotations.value(annotation, "inclusive"));
    }

    private static double number(Annotation annotation) {
        return Double.parseDouble(String.valueOf(Annotations.value(annotation, "value")));
    }

    private static void size(Annotation annotation, Schema schema, boolean text, boolean array) {
        Object min = Annotations.value(annotation, "min");
        Object max = Annotations.value(annotation, "max");
        if (min instanceof Integer && (Integer) min > 0) {
            if (text) {
                minLength(schema, (Integer) min);
            } else if (array) {
                minItems(schema, (Integer) min);
            }
        }
        if (max instanceof Integer && (Integer) max < Integer.MAX_VALUE) {
            if (text) {
                schema.setMaxLength((Integer) max);
            } else if (array) {
                schema.setMaxItems((Integer) max);
            }
        }
    }

    private static void minLength(Schema schema, int value) {
        if (schema.getMinLength() == null || schema.getMinLength() < value) {
            schema.setMinLength(value);
        }
    }

    private static void minItems(Schema schema, int value) {
        if (schema.getMinItems() == null || schema.getMinItems() < value) {
            schema.setMinItems(value);
        }
    }

    private static void minimum(Schema schema, double value) {
        if (schema.getMinimum() == null || schema.getMinimum() < value) {
            schema.setMinimum(value);
        }
    }

    private static void maximum(Schema schema, double value) {
        if (schema.getMaximum() == null || schema.getMaximum() > value) {
            schema.setMaximum(value);
        }
    }
}
