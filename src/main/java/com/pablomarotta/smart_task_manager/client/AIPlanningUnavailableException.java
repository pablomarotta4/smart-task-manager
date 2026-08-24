package com.pablomarotta.smart_task_manager.client;

public class AIPlanningUnavailableException extends RuntimeException {
    public AIPlanningUnavailableException(String message) {
        super(message);
    }

    public AIPlanningUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
