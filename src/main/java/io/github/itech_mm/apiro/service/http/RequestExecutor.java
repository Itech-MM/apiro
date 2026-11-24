package io.github.itech_mm.apiro.service.http;

import io.github.itech_framework.api_client.exceptions.ApiException;
import io.github.itech_framework.api_client.exceptions.RequestCancelledException;
import io.github.itech_framework.api_client.utils.CancelToken;
import io.github.itech_framework.api_client.utils.RequestOptions;
import io.github.itech_framework.api_client.utils.body.MultipartBody;
import io.github.itech_framework.api_client.utils.response.ApiExchange;
import io.github.itech_framework.api_client.utils.response.Timing;
import io.github.itech_mm.apiro.model.BodyType;
import io.github.itech_mm.apiro.model.FormPart;
import io.github.itech_mm.apiro.model.HttpMethod;
import io.github.itech_mm.apiro.model.KeyValue;
import io.github.itech_mm.apiro.model.PartKind;
import io.github.itech_mm.apiro.model.RequestBody;
import io.github.itech_mm.apiro.model.RequestSettings;
import io.github.itech_mm.apiro.model.ResponseData;
import io.github.itech_mm.apiro.service.http.RequestPreparer.PreparedRequest;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;

import javax.net.ssl.SSLException;
import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import io.github.itech_framework.core.annotations.components.levels.Service;

@Service
public class RequestExecutor {

    private static final Pattern CHARSET = Pattern.compile("charset=\"?([^;\"\\s]+)", Pattern.CASE_INSENSITIVE);

    private final HttpCalls calls;

    public RequestExecutor(HttpCalls calls) {
        this.calls = Objects.requireNonNull(calls);
    }

    public SendOutcome send(PreparedRequest request, CancelToken token) {
        Objects.requireNonNull(request);
        Objects.requireNonNull(token);
        String url = request.url() == null ? "" : request.url().trim();
        String lowerUrl = url.toLowerCase(Locale.ROOT);
        if (!lowerUrl.startsWith("http://") && !lowerUrl.startsWith("https://")) {
            return new SendOutcome.Failed(FailureKind.INVALID_REQUEST, "URL must start with http:// or https://");
        }
        RequestBody body = request.body() == null ? RequestBody.none() : request.body();
        String fileProblem = fileProblem(body);
        if (fileProblem != null) {
            return new SendOutcome.Failed(FailureKind.FILE_MISSING, fileProblem);
        }
        List<NameValuePair> headers = headersFor(request.headers(), body);
        Object payload = payloadFor(body);
        RequestOptions options = optionsFor(request.settings());
        try {
            ApiExchange<byte[]> exchange = dispatch(request.method(), url, headers, payload, options, token);
            return new SendOutcome.Done(map(exchange));
        } catch (ApiException error) {
            return classify(error, token);
        } catch (RuntimeException error) {
            if (token.isCancelled()) {
                return new SendOutcome.Cancelled();
            }
            return new SendOutcome.Failed(FailureKind.OTHER, describe(error));
        }
    }

    private ApiExchange<byte[]> dispatch(HttpMethod method, String url, List<NameValuePair> headers, Object body,
                                         RequestOptions options, CancelToken token) {
        return switch (method) {
            case GET -> calls.get(url, headers, body, options, token);
            case POST -> calls.post(url, headers, body, options, token);
            case PUT -> calls.put(url, headers, body, options, token);
            case PATCH -> calls.patch(url, headers, body, options, token);
            case DELETE -> calls.delete(url, headers, body, options, token);
            case OPTIONS -> calls.options(url, headers, body, options, token);
            case HEAD -> calls.head(url, headers, options, token);
        };
    }

    private static List<NameValuePair> headersFor(List<KeyValue> source, RequestBody body) {
        List<NameValuePair> result = new ArrayList<>();
        if (source == null) {
            return result;
        }
        for (KeyValue header : source) {
            if (!header.enabled() || header.key().isBlank()) {
                continue;
            }
            String value = header.value();
            if (header.key().trim().equalsIgnoreCase("Content-Type")) {
                value = withCharset(value, body);
            }
            result.add(new BasicNameValuePair(header.key().trim(), value));
        }
        return result;
    }

    private static String withCharset(String value, RequestBody body) {
        if (!body.type().isText() || value.toLowerCase(Locale.ROOT).contains("charset=")) {
            return value;
        }
        if (body.charset().equalsIgnoreCase("UTF-8")) {
            return value;
        }
        return value + "; charset=" + body.charset();
    }

    private static Object payloadFor(RequestBody body) {
        return switch (body.type()) {
            case NONE -> null;
            case JSON, XML, TEXT, HTML -> body.text();
            case FORM -> formFor(body);
            case MULTIPART -> multipartFor(body);
            case BINARY -> Path.of(body.filePath());
        };
    }

    private static List<NameValuePair> formFor(RequestBody body) {
        List<NameValuePair> fields = new ArrayList<>();
        for (KeyValue field : body.form()) {
            if (field.enabled() && !field.key().isBlank()) {
                fields.add(new BasicNameValuePair(field.key(), field.value()));
            }
        }
        return fields;
    }

    private static MultipartBody multipartFor(RequestBody body) {
        MultipartBody multipart = MultipartBody.create();
        for (FormPart part : body.parts()) {
            if (!part.enabled() || part.name().isBlank()) {
                continue;
            }
            if (part.kind() == PartKind.FILE) {
                multipart.file(part.name(), Path.of(part.filePath()));
            } else {
                multipart.field(part.name(), part.value());
            }
        }
        return multipart;
    }

    private static String fileProblem(RequestBody body) {
        if (body.type() == BodyType.BINARY) {
            return checkFile(body.filePath());
        }
        if (body.type() == BodyType.MULTIPART) {
            for (FormPart part : body.parts()) {
                if (part.enabled() && !part.name().isBlank() && part.kind() == PartKind.FILE) {
                    String problem = checkFile(part.filePath());
                    if (problem != null) {
                        return problem;
                    }
                }
            }
        }
        return null;
    }

    private static String checkFile(String filePath) {
        if (filePath.isBlank()) {
            return "No file selected";
        }
        try {
            return Files.isRegularFile(Path.of(filePath)) ? null : "File not found: " + filePath;
        } catch (InvalidPathException error) {
            return "Invalid file path: " + filePath;
        }
    }

    private static RequestOptions optionsFor(RequestSettings settings) {
        RequestSettings effective = settings == null ? RequestSettings.defaults().withDefaults() : settings.withDefaults();
        RequestOptions.Builder builder = RequestOptions.builder()
                .connectTimeoutMillis(Math.max(0, effective.connectTimeoutMillis()))
                .socketTimeoutMillis(Math.max(0, effective.socketTimeoutMillis()))
                .followRedirects(effective.followRedirects())
                .maxRedirects(Math.max(0, effective.maxRedirects()));
        Integer port = effective.proxyPort();
        if (!effective.proxyHost().isBlank() && port != null && port >= 1 && port <= 65535) {
            builder.proxy(effective.proxyHost(), port);
        }
        return builder.build();
    }

    private static ExchangeResult map(ApiExchange<byte[]> exchange) {
        Map<String, List<String>> headerMap = exchange.response().headers();
        byte[] bytes = exchange.body() == null ? new byte[0] : exchange.body();
        List<KeyValue> headers = new ArrayList<>();
        List<KeyValue> cookies = new ArrayList<>();
        if (headerMap != null) {
            for (Map.Entry<String, List<String>> entry : headerMap.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                for (String value : entry.getValue()) {
                    headers.add(KeyValue.of(entry.getKey(), value));
                    if (entry.getKey().equalsIgnoreCase("Set-Cookie")) {
                        cookies.add(parseCookie(value));
                    }
                }
            }
        }
        Timing timing = exchange.timing();
        long total = timing == null ? 0 : timing.totalMillis();
        long headersMillis = timing == null ? 0 : timing.headersMillis();
        int status = exchange.status();
        ResponseData data = new ResponseData(status, HttpStatusText.of(status), total, headersMillis, bytes.length,
                exchange.attempts(), headers, cookies, new String(bytes, charsetOf(headerMap)));
        return new ExchangeResult(data, exchange.request(), exchange.redirects());
    }

    private static KeyValue parseCookie(String setCookie) {
        String value = setCookie == null ? "" : setCookie;
        int semicolon = value.indexOf(';');
        String pair = semicolon < 0 ? value : value.substring(0, semicolon);
        int equals = pair.indexOf('=');
        if (equals < 0) {
            return KeyValue.of(pair.trim(), "");
        }
        return KeyValue.of(pair.substring(0, equals).trim(), pair.substring(equals + 1).trim());
    }

    private static Charset charsetOf(Map<String, List<String>> headers) {
        if (headers == null) {
            return StandardCharsets.UTF_8;
        }
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (entry.getKey() == null || !entry.getKey().equalsIgnoreCase("Content-Type")
                    || entry.getValue() == null || entry.getValue().isEmpty()) {
                continue;
            }
            Matcher matcher = CHARSET.matcher(entry.getValue().get(0));
            if (matcher.find()) {
                try {
                    return Charset.forName(matcher.group(1));
                } catch (IllegalArgumentException error) {
                    return StandardCharsets.UTF_8;
                }
            }
        }
        return StandardCharsets.UTF_8;
    }

    private static SendOutcome classify(ApiException error, CancelToken token) {
        if (token.isCancelled() || hasCause(error, RequestCancelledException.class)) {
            return new SendOutcome.Cancelled();
        }
        if ("BODY_TOO_LARGE".equals(error.getErrorCode())) {
            return new SendOutcome.Failed(FailureKind.BODY_TOO_LARGE, "The response is larger than the allowed size");
        }
        if (hasCause(error, UnknownHostException.class)) {
            return new SendOutcome.Failed(FailureKind.UNKNOWN_HOST, describe(error));
        }
        if (hasCause(error, SSLException.class)) {
            return new SendOutcome.Failed(FailureKind.TLS, describe(error));
        }
        if (hasCause(error, InterruptedIOException.class)) {
            return new SendOutcome.Failed(FailureKind.TIMEOUT, describe(error));
        }
        if (hasCause(error, ConnectException.class) || hasCause(error, NoRouteToHostException.class)) {
            return new SendOutcome.Failed(FailureKind.CONNECTION, describe(error));
        }
        return new SendOutcome.Failed(FailureKind.OTHER, describe(error));
    }

    private static boolean hasCause(Throwable error, Class<? extends Throwable> type) {
        Throwable current = error;
        for (int depth = 0; current != null && depth < 10; depth++) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static String describe(Throwable error) {
        Throwable last = error;
        Throwable current = error;
        for (int depth = 0; current != null && depth < 10; depth++) {
            last = current;
            current = current.getCause();
        }
        String message = last.getMessage();
        if (message == null || message.isBlank()) {
            message = error.getMessage();
        }
        if (message == null || message.isBlank()) {
            message = last.getClass().getSimpleName();
        }
        return message;
    }
}