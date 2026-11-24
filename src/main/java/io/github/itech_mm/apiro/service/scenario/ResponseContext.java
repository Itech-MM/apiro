package io.github.itech_mm.apiro.service.scenario;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import io.github.itech_mm.apiro.model.KeyValue;
import io.github.itech_mm.apiro.model.ResponseData;
import java.util.Optional;

public final class ResponseContext {

    private final ResponseData response;
    private JsonElement json;
    private boolean parsed;

    private ResponseContext(ResponseData response) {
        this.response = response;
    }

    public static ResponseContext of(ResponseData response) {
        return new ResponseContext(response);
    }

    public ResponseData response() {
        return response;
    }

    public int status() {
        return response.status();
    }

    public long millis() {
        return response.totalMillis();
    }

    public String body() {
        return response.body();
    }

    public Optional<String> header(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        String wanted = name.trim();
        for (KeyValue header : response.headers()) {
            if (header.key().equalsIgnoreCase(wanted)) {
                return Optional.of(header.value());
            }
        }
        return Optional.empty();
    }

    public Optional<JsonElement> jsonValue(String path) {
        JsonPath compiled = JsonPath.compile(path);
        JsonElement root = json();
        if (root == null) {
            throw new IllegalArgumentException("Response body is not JSON");
        }
        return compiled.evaluate(root);
    }

    public static String text(JsonElement element) {
        if (element.isJsonNull()) {
            return "null";
        }
        if (element.isJsonPrimitive()) {
            return element.getAsString();
        }
        return element.toString();
    }

    private JsonElement json() {
        if (!parsed) {
            parsed = true;
            String trimmed = response.body().trim();
            if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                try {
                    json = JsonParser.parseString(trimmed);
                } catch (JsonParseException e) {
                    json = null;
                }
            }
        }
        return json;
    }
}
