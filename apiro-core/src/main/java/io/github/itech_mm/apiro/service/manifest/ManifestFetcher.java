package io.github.itech_mm.apiro.service.manifest;

import io.github.itech_mm.apiro.spec.Manifest;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;

public final class ManifestFetcher {

    public static final String TOKEN_HEADER = "X-Apiro-Token";

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final int MAX_CHARS = 20_000_000;

    private ManifestFetcher() {
    }

    public static Manifest fetch(String address, String token) {
        URI uri = uri(address);
        HttpRequest.Builder request = HttpRequest.newBuilder(uri).timeout(TIMEOUT).GET()
                .header("Accept", "application/json");
        if (token != null && !token.isBlank()) {
            request.header(TOKEN_HEADER, token.trim());
        }
        HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL).build();
        HttpResponse<String> response;
        try {
            response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new ManifestException("Loading the manifest was interrupted", error);
        } catch (IOException | RuntimeException error) {
            String reason = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
            throw new ManifestException("Could not reach " + uri.getAuthority() + ": " + reason, error);
        }
        int status = response.statusCode();
        if (status == 401 || status == 403) {
            throw new ManifestException("The server refused the request (HTTP " + status
                    + "). Check the access token.");
        }
        if (status == 404) {
            throw new ManifestException("Nothing at " + uri.getPath()
                    + " (HTTP 404). Is the Apiro SDK enabled in that application?");
        }
        if (status < 200 || status >= 300) {
            throw new ManifestException("The server answered HTTP " + status);
        }
        String body = response.body();
        if (body != null && body.length() > MAX_CHARS) {
            throw new ManifestException("The manifest is larger than the 20 MB limit");
        }
        return ManifestReader.read(body);
    }

    public static String origin(String address) {
        try {
            URI uri = URI.create(address == null ? "" : address.trim());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if ((scheme.equals("http") || scheme.equals("https")) && uri.getRawAuthority() != null) {
                return scheme + "://" + uri.getRawAuthority();
            }
        } catch (IllegalArgumentException error) {
            return "";
        }
        return "";
    }

    private static URI uri(String address) {
        String text = address == null ? "" : address.trim();
        if (text.isEmpty()) {
            throw new ManifestException("Enter the address of the manifest");
        }
        if (origin(text).isEmpty()) {
            throw new ManifestException("The address must start with http:// or https://");
        }
        return URI.create(text);
    }
}
