package io.github.itech_mm.apiro.http;

import io.github.itech_framework.api_client.config.ApiClientConfig;
import io.github.itech_framework.api_client.handlers.DynamicApiClientInvocationHandler;

import java.lang.reflect.Proxy;

public final class ApiClientFactory implements AutoCloseable {

    private final DynamicApiClientInvocationHandler handler;
    private final HttpCalls calls;

    public ApiClientFactory(ClientSettings settings) {
        ApiClientConfig config = ApiClientConfig.builder()
                .throwOnError(false)
                .normalizeUri(false)
                .maxRetries(settings.maxRetries())
                .maxBodyBytes(settings.maxBodyBytes())
                .sslVerification(settings.sslVerification())
                .cookiesEnabled(false)
                .enableAsync(false)
                .enableLogging(false)
                .build();
        this.handler = new DynamicApiClientInvocationHandler(HttpCalls.class, config);
        this.calls = (HttpCalls) Proxy.newProxyInstance(
                HttpCalls.class.getClassLoader(), new Class<?>[] { HttpCalls.class }, handler);
    }

    public HttpCalls calls() {
        return calls;
    }

    @Override
    public void close() {
        handler.close();
    }
}