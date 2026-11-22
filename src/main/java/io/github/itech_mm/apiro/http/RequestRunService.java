package io.github.itech_mm.apiro.http;

import io.github.itech_framework.api_client.utils.CancelToken;
import io.github.itech_framework.core.annotations.components.levels.Service;
import io.github.itech_framework.core.annotations.dependency.Use;
import io.github.itech_framework.core.annotations.methods.PreDestroy;
import io.github.itech_framework.java_fx.utils.concurrent.BackgroundTaskService;
import io.github.itech_mm.apiro.http.RequestPreparer.PreparedRequest;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;

import java.util.function.Consumer;

@Service
public class RequestRunService {

    private final ReadOnlyBooleanWrapper running = new ReadOnlyBooleanWrapper(false);
    private volatile CancelToken current;

    @Use
    private RequestExecutor executor;

    public ReadOnlyBooleanProperty runningProperty() {
        return running.getReadOnlyProperty();
    }

    public boolean run(PreparedRequest request, Consumer<SendOutcome> onFinished) {
        if (running.get()) {
            return false;
        }
        CancelToken token = new CancelToken();
        current = token;
        running.set(true);
        BackgroundTaskService.getInstance().executeTask(
                () -> executor.send(request, token),
                outcome -> finish(token, outcome, onFinished),
                error -> finish(token, failure(error), onFinished));
        return true;
    }

    public void cancel() {
        CancelToken token = current;
        if (token != null) {
            token.cancel();
        }
    }

    @PreDestroy
    public void shutdown() {
        cancel();
    }

    private void finish(CancelToken token, SendOutcome outcome, Consumer<SendOutcome> onFinished) {
        if (current == token) {
            current = null;
        }
        running.set(false);
        onFinished.accept(outcome);
    }

    private static SendOutcome failure(Exception error) {
        String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        return new SendOutcome.Failed(FailureKind.OTHER, message);
    }
}