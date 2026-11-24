package io.github.itech_mm.apiro.service.http;

import io.github.itech_framework.api_client.utils.CancelToken;
import io.github.itech_framework.core.annotations.components.levels.Service;
import io.github.itech_framework.core.annotations.dependency.Use;
import io.github.itech_framework.core.annotations.methods.PreDestroy;
import io.github.itech_mm.apiro.service.http.RequestPreparer.PreparedRequest;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableSet;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

@Service
public class RequestRunService {

    private static final int SEND_THREADS = 16;

    private final ObservableSet<String> running = FXCollections.observableSet();
    private final ObservableSet<String> runningView = FXCollections.unmodifiableObservableSet(running);
    private final Map<String, CancelToken> tokens = new ConcurrentHashMap<>();
    private final ExecutorService pool = createPool();

    @Use
    private RequestExecutor executor;

    public ObservableSet<String> runningIds() {
        return runningView;
    }

    public boolean isRunning(String requestId) {
        return requestId != null && running.contains(requestId);
    }

    public boolean run(String requestId, PreparedRequest request, Consumer<SendOutcome> onFinished) {
        if (requestId == null) {
            return false;
        }
        CancelToken token = new CancelToken();
        if (tokens.putIfAbsent(requestId, token) != null) {
            return false;
        }
        running.add(requestId);
        try {
            CompletableFuture.supplyAsync(() -> executor.send(request, token), pool)
                    .whenComplete((outcome, error) -> Platform.runLater(
                            () -> finish(requestId, token, outcome != null ? outcome : failure(error), onFinished)));
        } catch (RejectedExecutionException error) {
            finish(requestId, token, failure(error), onFinished);
        }
        return true;
    }

    public void cancel(String requestId) {
        if (requestId == null) {
            return;
        }
        CancelToken token = tokens.get(requestId);
        if (token != null) {
            token.cancel();
        }
    }

    public void cancelAll() {
        tokens.values().forEach(CancelToken::cancel);
    }

    @PreDestroy
    public void shutdown() {
        cancelAll();
        pool.shutdownNow();
    }

    private void finish(String requestId, CancelToken token, SendOutcome outcome, Consumer<SendOutcome> onFinished) {
        tokens.remove(requestId, token);
        running.remove(requestId);
        onFinished.accept(outcome);
    }

    private static SendOutcome failure(Throwable error) {
        Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
        String message = cause == null || cause.getMessage() == null
                ? (cause == null ? "Unknown error" : cause.getClass().getSimpleName())
                : cause.getMessage();
        return new SendOutcome.Failed(FailureKind.OTHER, message);
    }

    private static ExecutorService createPool() {
        AtomicInteger counter = new AtomicInteger();
        ThreadPoolExecutor created = new ThreadPoolExecutor(SEND_THREADS, SEND_THREADS, 30, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(), runnable -> {
                    Thread thread = new Thread(runnable, "apiro-send-" + counter.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                });
        created.allowCoreThreadTimeOut(true);
        return created;
    }
}