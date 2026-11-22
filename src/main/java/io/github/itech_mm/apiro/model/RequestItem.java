package io.github.itech_mm.apiro.model;

import java.util.List;
import java.util.UUID;

public record RequestItem(String id, String name, HttpMethod method, String url, List<KeyValue> query,
                          List<KeyValue> headers, AuthConfig auth, RequestBody body,
                          RequestSettings settings) implements ProjectItem {

    public RequestItem {
        id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
        name = name == null ? "" : name;
        method = method == null ? HttpMethod.GET : method;
        url = url == null ? "" : url;
        query = query == null ? List.of() : List.copyOf(query);
        headers = headers == null ? List.of() : List.copyOf(headers);
        body = body == null ? RequestBody.none() : body;
    }

    public static RequestItem of(String name, HttpMethod method, String url) {
        return new RequestItem(null, name, method, url, null, null, null, null, null);
    }
}