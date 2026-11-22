package io.github.itech_mm.apiro.http;

import io.github.itech_framework.api_client.utils.response.RedirectHop;
import io.github.itech_framework.api_client.utils.response.SentRequest;
import io.github.itech_mm.apiro.model.ResponseData;

import java.util.List;

public record ExchangeResult(ResponseData response, SentRequest sent, List<RedirectHop> redirects) {

    public ExchangeResult {
        redirects = redirects == null ? List.of() : List.copyOf(redirects);
    }
}