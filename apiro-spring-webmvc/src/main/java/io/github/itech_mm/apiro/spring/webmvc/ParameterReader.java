package io.github.itech_mm.apiro.spring.webmvc;

import io.github.itech_mm.apiro.spec.Body;
import io.github.itech_mm.apiro.spec.Parameter;
import io.github.itech_mm.apiro.spec.ParameterLocation;
import io.github.itech_mm.apiro.spec.Schema;
import io.github.itech_mm.apiro.spec.SchemaType;
import java.lang.annotation.Annotation;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TimeZone;
import org.springframework.beans.BeanUtils;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.MethodParameter;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.ResolvableType;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ValueConstants;

final class ParameterReader {
    private static final ParameterNameDiscoverer NAMES = new DefaultParameterNameDiscoverer();

    private final PathTemplate template;
    private final Set<String> consumes;
    private final String where;
    private final Set<String> warnings;
    private final SchemaMapper schemas;
    private final Map<String, Parameter> parameters = new LinkedHashMap<>();
    private Body body;

    ParameterReader(PathTemplate template, Set<String> consumes, String where, Set<String> warnings,
                    SchemaMapper schemas) {
        this.template = template;
        this.consumes = consumes;
        this.where = where;
        this.warnings = warnings;
        this.schemas = schemas;
    }

    void read(MethodParameter[] declared) {
        for (MethodParameter parameter : declared) {
            read(parameter);
        }
        for (String name : template.variables().keySet()) {
            if (!parameters.containsKey(key(ParameterLocation.PATH, name))) {
                add(path(name, ResolvableType.forClass(String.class)));
            }
        }
    }

    List<Parameter> parameters() {
        return new ArrayList<>(parameters.values());
    }

    Body body() {
        return body;
    }

    private void read(MethodParameter parameter) {
        parameter.initParameterNameDiscovery(NAMES);
        ResolvableType type = ResolvableType.forMethodParameter(parameter);
        boolean optional = type.resolve() == Optional.class;
        if (optional) {
            type = type.getGeneric(0);
        }
        Class<?> raw = type.resolve(Object.class);
        PathVariable path = parameter.getParameterAnnotation(PathVariable.class);
        RequestParam query = parameter.getParameterAnnotation(RequestParam.class);
        RequestHeader header = parameter.getParameterAnnotation(RequestHeader.class);
        CookieValue cookie = parameter.getParameterAnnotation(CookieValue.class);
        RequestPart part = parameter.getParameterAnnotation(RequestPart.class);
        RequestBody requestBody = parameter.getParameterAnnotation(RequestBody.class);
        if (path != null) {
            String name = name(parameter, path.name(), path.value(), raw);
            if (name != null && template.variables().containsKey(name)) {
                Parameter found = path(name, type);
                Constraints.apply(parameter.getParameterAnnotations(), found.getSchema());
                add(found);
            }
        } else if (query != null) {
            named(parameter, name(parameter, query.name(), query.value(), raw), location(type), type,
                    query.required() && !optional, query.defaultValue());
        } else if (header != null) {
            if (raw != HttpHeaders.class) {
                named(parameter, name(parameter, header.name(), header.value(), raw), ParameterLocation.HEADER, type,
                        header.required() && !optional, header.defaultValue());
            }
        } else if (cookie != null) {
            named(parameter, name(parameter, cookie.name(), cookie.value(), raw), ParameterLocation.COOKIE, type,
                    cookie.required() && !optional, cookie.defaultValue());
        } else if (part != null) {
            String name = name(parameter, part.name(), part.value(), raw);
            if (name != null) {
                add(new Parameter().setName(name).setIn(ParameterLocation.PART)
                        .setRequired(part.required() && !optional)
                        .setSchema(SimpleSchemas.of(type, SchemaType.ANY)));
            }
        } else if (requestBody != null) {
            body = new Body().setContentType(consumes.isEmpty() ? null : consumes.iterator().next())
                    .setRequired(requestBody.required() && !optional)
                    .setSchema(schemas.map(type));
        } else if (parameter.hasParameterAnnotation(ModelAttribute.class)) {
            warnings.add(where + ": @ModelAttribute parameters are not read yet");
        } else if (!claimed(parameter)) {
            if (isFile(type)) {
                named(parameter, parameter.getParameterName(), ParameterLocation.PART, type, !optional,
                        ValueConstants.DEFAULT_NONE);
            } else if (isSimple(raw)) {
                named(parameter, parameter.getParameterName(), location(type), type, false,
                        ValueConstants.DEFAULT_NONE);
            }
        }
    }

    private void named(MethodParameter parameter, String name, String location, ResolvableType type,
                       boolean required, String defaultValue) {
        if (name == null) {
            if (!Map.class.isAssignableFrom(type.resolve(Object.class))) {
                warnings.add(where + ": parameter " + parameter.getParameterIndex()
                        + " has no name, compile with -parameters");
            }
            return;
        }
        boolean hasDefault = !ValueConstants.DEFAULT_NONE.equals(defaultValue);
        Schema schema = SimpleSchemas.of(type, SchemaType.STRING);
        Constraints.apply(parameter.getParameterAnnotations(), schema);
        add(new Parameter().setName(name).setIn(location).setRequired(required && !hasDefault)
                .setDefaultValue(hasDefault ? defaultValue : null)
                .setSchema(schema));
    }

    private String name(MethodParameter parameter, String name, String value, Class<?> raw) {
        if (!name.isEmpty()) {
            return name;
        }
        if (!value.isEmpty()) {
            return value;
        }
        if (Map.class.isAssignableFrom(raw)) {
            return null;
        }
        String discovered = parameter.getParameterName();
        if (discovered == null) {
            warnings.add(where + ": parameter " + parameter.getParameterIndex()
                    + " has no name, compile with -parameters");
        }
        return discovered;
    }

    private Parameter path(String name, ResolvableType type) {
        Parameter parameter = new Parameter().setName(name).setIn(ParameterLocation.PATH).setRequired(true)
                .setSchema(SimpleSchemas.of(type, SchemaType.STRING));
        parameter.getSchema().setPattern(template.variables().get(name));
        return parameter;
    }

    private String location(ResolvableType type) {
        if (isFile(type)) {
            return ParameterLocation.PART;
        }
        for (String media : consumes) {
            if (media.startsWith("multipart/")) {
                return ParameterLocation.PART;
            }
            if (media.startsWith("application/x-www-form-urlencoded")) {
                return ParameterLocation.FORM;
            }
        }
        return ParameterLocation.QUERY;
    }

    private void add(Parameter parameter) {
        parameters.putIfAbsent(key(parameter.getIn(), parameter.getName()), parameter);
    }

    private static String key(String location, String name) {
        return location + " " + name;
    }

    private static boolean isFile(ResolvableType type) {
        Class<?> raw = type.resolve(Object.class);
        if (raw.isArray()) {
            raw = type.getComponentType().resolve(Object.class);
        } else if (Iterable.class.isAssignableFrom(raw)) {
            raw = type.as(Iterable.class).getGeneric(0).resolve(Object.class);
        }
        return SimpleSchemas.isFile(raw);
    }

    private static boolean isSimple(Class<?> type) {
        return BeanUtils.isSimpleProperty(type) && type != Locale.class && type != TimeZone.class
                && !ZoneId.class.isAssignableFrom(type) && type != Class.class
                && !type.getName().equals("org.springframework.http.HttpMethod");
    }

    private static boolean claimed(MethodParameter parameter) {
        for (Annotation annotation : parameter.getParameterAnnotations()) {
            String name = annotation.annotationType().getName();
            if (name.startsWith("org.springframework.") && !name.startsWith("org.springframework.lang.")
                    && !name.startsWith("org.springframework.validation.")) {
                return true;
            }
        }
        return false;
    }
}
