package com.shop;

import org.springframework.web.bind.annotation.RequestParam;

public class WebhookController {
    public void receive(@RequestParam(value = "event", required = false) String event) {
    }
}
