package io.github.itech_mm.apiro.service.http;

import io.github.itech_framework.api_client.config.ApiClientConfig;
import io.github.itech_framework.api_client.handlers.DynamicApiClientInvocationHandler;

import java.lang.reflect.Proxy;

final class TestApiClients implements AutoCloseable {

    private final DynamicApiClientInvocationHandler handler;
    private final HttpCalls calls;

    TestApiClients(long maxBodyBytes) {
        ApiClientConfig config = ApiClientConfig.builder()
                .throwOnError(false)
                .normalizeUri(false)
                .maxRetries(0)
                .maxBodyBytes(maxBodyBytes)
                .sslVerification(true)
                .cookiesEnabled(false)
                .enableAsync(false)
                .enableLogging(false)
                .build();
        this.handler = new DynamicApiClientInvocationHandler(HttpCalls.class, config);
        this.calls = (HttpCalls) Proxy.newProxyInstance(
                HttpCalls.class.getClassLoader(), new Class<?>[] { HttpCalls.class }, handler);
    }

    HttpCalls calls() {
        return calls;
    }

    @Override
    public void close() {
        handler.close();
    }
}