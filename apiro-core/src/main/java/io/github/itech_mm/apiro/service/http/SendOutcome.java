package io.github.itech_mm.apiro.service.http;

public sealed interface SendOutcome permits SendOutcome.Done, SendOutcome.Failed, SendOutcome.Cancelled {

    record Done(ExchangeResult result) implements SendOutcome {
    }

    record Failed(FailureKind kind, String message) implements SendOutcome {
    }

    record Cancelled() implements SendOutcome {
    }
}