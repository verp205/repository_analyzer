package com.ivan_naumenko.repository_analyzer.forge.exception;

import java.time.Instant;

/** API хостинга исчерпало лимит запросов; повторять раньше retryAt бессмысленно. */
public class ForgeRateLimitException extends RuntimeException {

    private final Instant retryAt;

    public ForgeRateLimitException(String message, Instant retryAt) {
        super(message);
        this.retryAt = retryAt;
    }

    public Instant retryAt() {
        return retryAt;
    }
}
