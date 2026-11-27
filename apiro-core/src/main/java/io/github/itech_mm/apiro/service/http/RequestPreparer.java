package io.github.itech_mm.apiro.service.http;

import io.github.itech_mm.apiro.model.AuthConfig;
import io.github.itech_mm.apiro.model.BodyOverride;
import io.github.itech_mm.apiro.model.BodyType;
import io.github.itech_mm.apiro.model.FormPart;
import io.github.itech_mm.apiro.model.HttpMethod;
import io.github.itech_mm.apiro.model.KeyValue;
import io.github.itech_mm.apiro.model.Project;
import io.github.itech_mm.apiro.model.RequestBody;
import io.github.itech_mm.apiro.model.RequestItem;
import io.github.itech_mm.apiro.model.RequestSettings;
import io.github.itech_mm.apiro.model.Variable;
import io.github.itech_mm.apiro.service.project.Inheritance;
import io.github.itech_mm.apiro.service.variables.VariableResolver;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class RequestPreparer {

    public record PreparedRequest(HttpMethod method, String url, List<KeyValue> headers, RequestBody body,
                                  RequestSettings settings, List<String> unresolved) {
    }

    private RequestPreparer() {
    }

    public static PreparedRequest prepare(Project project, String environmentId, String requestId) {
        return prepare(project, environmentId, requestId, List.of());
    }

    public static PreparedRequest prepare(Project project, String environmentId, String requestId,
                                          List<Variable> runtime) {
        return prepare(project, environmentId, requestId, runtime, List.of());
    }

    public static PreparedRequest prepare(Project project, String environmentId, String requestId,
                                          List<Variable> runtime, List<Variable> literal) {
        return prepare(project, environmentId, requestId, runtime, literal, List.of());
    }

    public static PreparedRequest prepare(Project project, String environmentId, String requestId,
                                          List<Variable> runtime, List<Variable> literal,
                                          List<KeyValue> headerOverrides) {
        return prepare(project, environmentId, requestId, runtime, literal, headerOverrides, null);
    }

    public static PreparedRequest prepare(Project project, String environmentId, String requestId,
                                          List<Variable> runtime, List<Variable> literal,
                                          List<KeyValue> headerOverrides, BodyOverride bodyOverride) {
        return prepare(project, environmentId, requestId, runtime, literal, headerOverrides, bodyOverride,
                List.of(), "");
    }

    public static PreparedRequest prepare(Project project, String environmentId, String requestId,
                                          List<Variable> runtime, List<Variable> literal,
                                          List<KeyValue> headerOverrides, BodyOverride bodyOverride,
                                          List<KeyValue> queryOverrides, String urlOverride) {
        RequestItem original = project.findItem(requestId)
                .filter(RequestItem.class::isInstance)
                .map(RequestItem.class::cast)
                .orElseThrow(() -> new IllegalArgumentException("Unknown request: " + requestId));
        RequestItem request = BodyOverrides.apply(original, bodyOverride);
        Inheritance.Effective effective = Inheritance.forItem(project, requestId).orElseThrow();
        VariableResolver resolver = VariableResolver.forItem(project, environmentId, requestId, runtime, literal);

        String rawUrl = urlOverride != null && !urlOverride.isBlank() ? urlOverride : request.url();
        String baseUrl = resolver.resolve(rawUrl);
        List<KeyValue> query = mergeQuery(request.query(), queryOverrides, resolver);
        String url = withQuery(baseUrl, query);
        List<KeyValue> headers = new ArrayList<>();
        for (KeyValue header : effective.headers()) {
            headers.add(new KeyValue(resolver.resolve(header.key()), resolver.resolve(header.value()), true));
        }
        applyOverrides(headers, headerOverrides, resolver);
        applyAuth(headers, effective.auth(), resolver);
        RequestBody body = resolveBody(request.body(), resolver);
        addContentType(headers, body);
        List<String> unresolved = unresolved(baseUrl, query, headers, body, effective.auth(), resolver);
        return new PreparedRequest(request.method(), url, List.copyOf(headers), body, effective.settings(),
                unresolved);
    }

    private static List<KeyValue> mergeQuery(List<KeyValue> base, List<KeyValue> overrides,
                                             VariableResolver resolver) {
        List<KeyValue> merged = new ArrayList<>();
        if (base != null) {
            for (KeyValue entry : base) {
                String key = resolver.resolve(entry.key());
                if (!entry.enabled() || key.isBlank()) {
                    continue;
                }
                merged.add(new KeyValue(key, resolver.resolve(entry.value()), true));
            }
        }
        if (overrides != null) {
            for (KeyValue override : overrides) {
                String key = resolver.resolve(override.key());
                if (!override.enabled() || key.isBlank()) {
                    continue;
                }
                KeyValue entry = new KeyValue(key, resolver.resolve(override.value()), true);
                int first = indexOfQuery(merged, key);
                if (first < 0) {
                    merged.add(entry);
                    continue;
                }
                merged.set(first, entry);
                for (int i = merged.size() - 1; i > first; i--) {
                    if (merged.get(i).key().equals(key)) {
                        merged.remove(i);
                    }
                }
            }
        }
        return merged;
    }

    private static int indexOfQuery(List<KeyValue> query, String key) {
        for (int i = 0; i < query.size(); i++) {
            if (query.get(i).key().equals(key)) {
                return i;
            }
        }
        return -1;
    }

    private static String withQuery(String url, List<KeyValue> query) {
        String result = url;
        for (KeyValue entry : query) {
            String key = entry.key();
            if (!entry.enabled() || key.isBlank()) {
                continue;
            }
            String pair = encode(key) + "=" + encode(entry.value());
            if (!result.contains("?")) {
                result = result + "?" + pair;
            } else if (result.endsWith("?") || result.endsWith("&")) {
                result = result + pair;
            } else {
                result = result + "&" + pair;
            }
        }
        return result;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static void applyOverrides(List<KeyValue> headers, List<KeyValue> overrides, VariableResolver resolver) {
        for (KeyValue override : overrides) {
            String name = resolver.resolve(override.key()).trim();
            if (!override.enabled() || name.isEmpty()) {
                continue;
            }
            KeyValue entry = new KeyValue(name, resolver.resolve(override.value()), true);
            int first = indexOfHeader(headers, name);
            if (first < 0) {
                headers.add(entry);
                continue;
            }
            headers.set(first, entry);
            for (int i = headers.size() - 1; i > first; i--) {
                if (headers.get(i).key().equalsIgnoreCase(name)) {
                    headers.remove(i);
                }
            }
        }
    }

    private static int indexOfHeader(List<KeyValue> headers, String name) {
        for (int i = 0; i < headers.size(); i++) {
            if (headers.get(i).key().equalsIgnoreCase(name)) {
                return i;
            }
        }
        return -1;
    }

    private static void applyAuth(List<KeyValue> headers, AuthConfig auth, VariableResolver resolver) {
        switch (auth.type()) {
            case BASIC -> {
                String credentials = resolver.resolve(auth.username()) + ":" + resolver.resolve(auth.password());
                setIfAbsent(headers, "Authorization",
                        "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8)));
            }
            case BEARER -> {
                String token = resolver.resolve(auth.token());
                if (!token.isBlank()) {
                    setIfAbsent(headers, "Authorization", "Bearer " + token);
                }
            }
            case API_KEY -> {
                String name = resolver.resolve(auth.keyName());
                if (!name.isBlank()) {
                    setIfAbsent(headers, name, resolver.resolve(auth.keyValue()));
                }
            }
            case NONE, INHERIT -> {
            }
        }
    }

    private static void setIfAbsent(List<KeyValue> headers, String name, String value) {
        if (!hasHeader(headers, name)) {
            headers.add(KeyValue.of(name, value));
        }
    }

    private static boolean hasHeader(List<KeyValue> headers, String name) {
        return headers.stream().anyMatch(header -> header.key().equalsIgnoreCase(name));
    }

    private static RequestBody resolveBody(RequestBody body, VariableResolver resolver) {
        if (body.type() == BodyType.NONE) {
            return RequestBody.none();
        }
        List<KeyValue> form = new ArrayList<>();
        for (KeyValue entry : body.form()) {
            if (entry.enabled()) {
                form.add(KeyValue.of(resolver.resolve(entry.key()), resolver.resolve(entry.value())));
            }
        }
        List<FormPart> parts = new ArrayList<>();
        for (FormPart part : body.parts()) {
            if (part.enabled()) {
                parts.add(new FormPart(resolver.resolve(part.name()), part.kind(), resolver.resolve(part.value()),
                        resolver.resolve(part.filePath()), true));
            }
        }
        return new RequestBody(body.type(), resolver.resolve(body.text()), body.contentType(), body.charset(), form,
                parts, resolver.resolve(body.filePath()));
    }

    private static void addContentType(List<KeyValue> headers, RequestBody body) {
        if (body.type() == BodyType.NONE || body.type() == BodyType.MULTIPART) {
            return;
        }
        setIfAbsent(headers, "Content-Type", body.effectiveContentType());
    }

    private static List<String> authTexts(AuthConfig auth) {
        return switch (auth.type()) {
            case BASIC -> List.of(auth.username(), auth.password());
            case BEARER -> List.of(auth.token());
            case API_KEY -> List.of(auth.keyName(), auth.keyValue());
            case NONE, INHERIT -> List.of();
        };
    }

    private static List<String> unresolved(String url, List<KeyValue> query, List<KeyValue> headers,
                                           RequestBody body, AuthConfig auth, VariableResolver resolver) {
        List<String> texts = new ArrayList<>();
        texts.add(url);
        for (KeyValue entry : query) {
            texts.add(entry.key());
            texts.add(entry.value());
        }
        texts.addAll(authTexts(auth));
        for (KeyValue header : headers) {
            texts.add(header.key());
            texts.add(header.value());
        }
        texts.add(body.text());
        texts.add(body.filePath());
        for (KeyValue entry : body.form()) {
            texts.add(entry.key());
            texts.add(entry.value());
        }
        for (FormPart part : body.parts()) {
            texts.add(part.name());
            texts.add(part.value());
            texts.add(part.filePath());
        }
        Set<String> names = new LinkedHashSet<>();
        for (String text : texts) {
            for (VariableResolver.Reference reference : resolver.scan(text)) {
                if (!reference.defined()) {
                    names.add(reference.name());
                }
            }
        }
        return List.copyOf(names);
    }
}