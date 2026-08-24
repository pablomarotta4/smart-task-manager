package com.pablomarotta.smart_task_manager.email;

public class EmailDeliveryException extends RuntimeException {

    public EmailDeliveryException(String failureCode) {
        super(failureCode);
    }

    public EmailDeliveryException(String failureCode, Throwable cause) {
        super(failureCode, cause);
    }

    public String failureCode() {
        return getMessage();
    }
}
