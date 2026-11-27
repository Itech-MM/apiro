package io.github.itech_mm.apiro.spring.webmvc;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

final class Annotations {
    private Annotations() {
    }

    static Object value(Annotation annotation, String attribute) {
        try {
            Method method = annotation.annotationType().getMethod(attribute);
            method.setAccessible(true);
            return method.invoke(annotation);
        } catch (NoSuchMethodException absent) {
            return null;
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot read " + attribute + " of " + annotation, error);
        }
    }
}
