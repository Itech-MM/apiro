package io.github.itech_mm.apiro.service.manifest;

import io.github.itech_mm.apiro.spec.Application;
import io.github.itech_mm.apiro.spec.Manifest;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;

public final class ManifestFetcher {

    public static final String TOKEN_HEADER = "X-Apiro-Token";

    private static final String MANIFEST_PATH = "/apiro/manifest";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final int MAX_BYTES = 20 * 1024 * 1024;
    private static final int MAX_REDIRECTS = 5;
    private static final Set<Integer> REDIRECTS = Set.of(301, 302, 303, 307, 308);

    private ManifestFetcher() {
    }

    public static Manifest fetch(String address, String token) {
        URI start = uri(address);
        String secret = token == null ? "" : token.trim();
        HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER).build();
        URI current = start;
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            boolean trusted = sameOrigin(start, current);
            HttpResponse<InputStream> response = send(client, current, trusted ? secret : "");
            try (InputStream body = response.body()) {
                int status = response.statusCode();
                if (REDIRECTS.contains(status)) {
                    current = redirect(current, response);
                    continue;
                }
                check(status, current, !trusted && !secret.isEmpty());
                byte[] bytes = body.readNBytes(MAX_BYTES + 1);
                if (bytes.length > MAX_BYTES) {
                    throw new ManifestException("The manifest is larger than the 20 MB limit");
                }
                Manifest manifest = ManifestReader.read(new String(bytes, StandardCharsets.UTF_8));
                return withContextPath(manifest, current);
            } catch (IOException error) {
                throw unreachable(current, error);
            }
        }
        throw new ManifestException("The address redirects too many times");
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

    private static HttpResponse<InputStream> send(HttpClient client, URI uri, String token) {
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(uri).timeout(TIMEOUT).GET()
                    .header("Accept", "application/json");
            if (!token.isEmpty()) {
                request.header(TOKEN_HEADER, token);
            }
            return client.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new ManifestException("Loading the manifest was interrupted", error);
        } catch (IOException | RuntimeException error) {
            throw unreachable(uri, error);
        }
    }

    private static ManifestException unreachable(URI uri, Exception error) {
        String reason = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        return new ManifestException("Could not reach " + uri.getAuthority() + ": " + reason, error);
    }

    private static boolean sameOrigin(URI first, URI second) {
        return first.getHost() != null && first.getHost().equalsIgnoreCase(second.getHost())
                && first.getScheme().equalsIgnoreCase(second.getScheme())
                && port(first) == port(second);
    }

    private static int port(URI uri) {
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }
        return uri.getScheme().equalsIgnoreCase("https") ? 443 : 80;
    }

    private static URI redirect(URI from, HttpResponse<?> response) {
        String location = response.headers().firstValue("Location").orElse("").trim();
        if (location.isEmpty()) {
            throw new ManifestException("The server answered HTTP " + response.statusCode()
                    + " without a redirect address");
        }
        URI target;
        try {
            target = from.resolve(location);
        } catch (IllegalArgumentException error) {
            throw new ManifestException("The server redirects to an invalid address", error);
        }
        if (origin(target.toString()).isEmpty()) {
            throw new ManifestException("The server redirects to an unsupported address: " + target);
        }
        if (from.getScheme().equalsIgnoreCase("https") && target.getScheme().equalsIgnoreCase("http")) {
            throw new ManifestException("The server redirects from https to http, which is not followed");
        }
        return target;
    }

    private static void check(int status, URI uri, boolean tokenWithheld) {
        if (status == 401 || status == 403) {
            String advice = tokenWithheld
                    ? " The token is not sent across a redirect to another origin. Enter " + uri + " as the address."
                    : " Check the access token.";
            throw new ManifestException("The server refused the request (HTTP " + status + ")." + advice);
        }
        if (status == 404) {
            throw new ManifestException("Nothing at " + uri.getPath()
                    + " (HTTP 404). Is the Apiro SDK enabled in that application?");
        }
        if (status < 200 || status >= 300) {
            throw new ManifestException("The server answered HTTP " + status);
        }
    }

    private static Manifest withContextPath(Manifest manifest, URI source) {
        Application application = manifest.getApplication();
        if (application != null && application.getContextPath() != null && !application.getContextPath().isBlank()) {
            return manifest;
        }
        String servletPath = application == null ? "" : slashed(application.getServletPath());
        String path = slashed(source.getRawPath());
        String suffix = servletPath + MANIFEST_PATH;
        if (!path.endsWith(suffix)) {
            return manifest;
        }
        String contextPath = slashed(path.substring(0, path.length() - suffix.length()));
        if (contextPath.isEmpty()) {
            return manifest;
        }
        Application target = application == null ? new Application() : application;
        manifest.setApplication(target.setContextPath(contextPath));
        return manifest;
    }

    private static String slashed(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String trimmed = value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        if (trimmed.isEmpty()) {
            return "";
        }
        return trimmed.startsWith("/") ? trimmed : "/" + trimmed;
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
