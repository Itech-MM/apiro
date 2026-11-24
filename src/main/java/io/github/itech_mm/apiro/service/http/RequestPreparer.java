package io.github.itech_mm.apiro.service.http;

import io.github.itech_mm.apiro.model.AuthConfig;
import io.github.itech_mm.apiro.model.BodyType;
import io.github.itech_mm.apiro.model.FormPart;
import io.github.itech_mm.apiro.model.HttpMethod;
import io.github.itech_mm.apiro.model.KeyValue;
import io.github.itech_mm.apiro.model.Project;
import io.github.itech_mm.apiro.model.RequestBody;
import io.github.itech_mm.apiro.model.RequestItem;
import io.github.itech_mm.apiro.model.RequestSettings;
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
        RequestItem request = project.findItem(requestId)
                .filter(RequestItem.class::isInstance)
                .map(RequestItem.class::cast)
                .orElseThrow(() -> new IllegalArgumentException("Unknown request: " + requestId));
        Inheritance.Effective effective = Inheritance.forItem(project, requestId).orElseThrow();
        VariableResolver resolver = VariableResolver.forItem(project, environmentId, requestId);

        String url = withQuery(resolver.resolve(request.url()), request.query(), resolver);
        List<KeyValue> headers = new ArrayList<>();
        for (KeyValue header : effective.headers()) {
            headers.add(new KeyValue(resolver.resolve(header.key()), resolver.resolve(header.value()), true));
        }
        applyAuth(headers, effective.auth(), resolver);
        RequestBody body = resolveBody(request.body(), resolver);
        addContentType(headers, body);
        List<String> unresolved = unresolved(url, headers, body, effective.auth(), resolver);
        return new PreparedRequest(request.method(), url, List.copyOf(headers), body, effective.settings(),
                unresolved);
    }

    private static String withQuery(String url, List<KeyValue> query, VariableResolver resolver) {
        String result = url;
        for (KeyValue entry : query) {
            String key = resolver.resolve(entry.key());
            if (!entry.enabled() || key.isBlank()) {
                continue;
            }
            String pair = encode(key) + "=" + encode(resolver.resolve(entry.value()));
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

    private static List<String> unresolved(String url, List<KeyValue> headers, RequestBody body,
                                           AuthConfig auth, VariableResolver resolver) {
        List<String> texts = new ArrayList<>();
        texts.add(url);
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