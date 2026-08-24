package com.pablomarotta.smart_task_manager.security;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
public final class AuthRateLimitExceededException extends RuntimeException {

    private final long retryAfterSeconds;

    public AuthRateLimitExceededException(long retryAfterSeconds) {
        super("Too many authentication requests");
        if (retryAfterSeconds < 1) {
            throw new IllegalArgumentException("Retry-After must be positive");
        }
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
