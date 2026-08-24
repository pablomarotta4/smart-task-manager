package com.pablomarotta.smart_task_manager.exception;

import org.springframework.http.HttpStatus;

public enum AccountActionErrorCode {
    ACCOUNT_ACTION_INVALID(HttpStatus.BAD_REQUEST),
    ACCOUNT_ACTION_EXPIRED(HttpStatus.GONE),
    ACCOUNT_ACTION_USED(HttpStatus.GONE),
    ACCOUNT_ACTION_SUPERSEDED(HttpStatus.GONE);

    private final HttpStatus status;

    AccountActionErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
