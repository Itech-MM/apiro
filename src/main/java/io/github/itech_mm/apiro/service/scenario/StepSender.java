package io.github.itech_mm.apiro.service.scenario;

import io.github.itech_framework.api_client.utils.CancelToken;
import io.github.itech_mm.apiro.service.http.RequestPreparer.PreparedRequest;
import io.github.itech_mm.apiro.service.http.SendOutcome;

@FunctionalInterface
public interface StepSender {

    SendOutcome send(PreparedRequest request, CancelToken token);
}
