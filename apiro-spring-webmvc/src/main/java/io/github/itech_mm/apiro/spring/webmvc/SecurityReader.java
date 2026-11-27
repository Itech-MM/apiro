package io.github.itech_mm.apiro.spring.webmvc;

import io.github.itech_mm.apiro.spec.AccessLevel;
import io.github.itech_mm.apiro.spec.Authentication;
import io.github.itech_mm.apiro.spec.Authorization;
import io.github.itech_mm.apiro.spec.Security;
import io.github.itech_mm.apiro.spec.SecuritySource;
import io.github.itech_mm.apiro.spec.SecurityStatus;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

final class SecurityReader {
    private SecurityReader() {
    }

    static Security read(Class<?> type, Method method, Collection<String> schemes) {
        String source = SecuritySource.METHOD_ANNOTATION;
        String access = access(method);
        for (Class<?> each = type; access == null && each != null; each = each.getSuperclass()) {
            source = SecuritySource.CLASS_ANNOTATION;
            access = access(each);
        }
        if (access == null) {
            return null;
        }
        Security security = new Security().setAuthorization(new Authorization()
                .setStatus(SecurityStatus.DECLARED).setAccess(access).setSource(source));
        if (AccessLevel.RESTRICTED.equals(access) && !schemes.isEmpty()) {
            security.setAuthentication(new Authentication().setStatus(SecurityStatus.DECLARED)
                    .setSchemes(new ArrayList<>(schemes)).setSource(SecuritySource.CONFIGURATION));
        }
        return security;
    }

    private static String access(AnnotatedElement element) {
        List<String> found = new ArrayList<>();
        for (Annotation annotation : element.getAnnotations()) {
            String direct = of(annotation);
            if (direct != null) {
                found.add(direct);
                continue;
            }
            for (Annotation meta : annotation.annotationType().getAnnotations()) {
                String inherited = of(meta);
                if (inherited != null) {
                    found.add(inherited);
                }
            }
        }
        if (found.contains(AccessLevel.RESTRICTED)) {
            return AccessLevel.RESTRICTED;
        }
        return found.isEmpty() ? null : AccessLevel.OPEN;
    }

    private static String of(Annotation annotation) {
        switch (annotation.annotationType().getName()) {
            case "org.springframework.security.access.prepost.PreAuthorize":
                String expression = String.valueOf(Annotations.value(annotation, "value")).trim();
                return expression.equals("permitAll") || expression.equals("permitAll()")
                        ? AccessLevel.OPEN : AccessLevel.RESTRICTED;
            case "org.springframework.security.access.annotation.Secured":
            case "javax.annotation.security.RolesAllowed":
            case "jakarta.annotation.security.RolesAllowed":
            case "javax.annotation.security.DenyAll":
            case "jakarta.annotation.security.DenyAll":
                return AccessLevel.RESTRICTED;
            case "javax.annotation.security.PermitAll":
            case "jakarta.annotation.security.PermitAll":
                return AccessLevel.OPEN;
            default:
                return null;
        }
    }
}
