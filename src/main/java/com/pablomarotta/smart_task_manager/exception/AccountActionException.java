package com.pablomarotta.smart_task_manager.exception;

public class AccountActionException extends RuntimeException {

    private final AccountActionErrorCode code;

    public AccountActionException(AccountActionErrorCode code) {
        super(code.name());
        this.code = code;
    }

    public AccountActionErrorCode getCode() {
        return code;
    }
}
