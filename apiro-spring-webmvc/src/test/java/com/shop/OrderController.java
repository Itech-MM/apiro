package com.shop;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@PreAuthorize("isAuthenticated()")
public class OrderController {
    @PreAuthorize("hasRole('ADMIN')")
    public void create(@PathVariable("customerId") long customerId,
                       @RequestParam(value = "dryRun", defaultValue = "false") boolean dryRun,
                       @RequestHeader(value = "X-Trace", required = false) String trace,
                       @RequestBody CreateOrder order) {
    }

    @Deprecated
    public void get(@PathVariable("id") long id) {
    }
}
