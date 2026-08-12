package com.pablomarotta.smart_task_manager.service;

import com.pablomarotta.smart_task_manager.model.AccountActionPurpose;

import java.util.UUID;

public record EmailOutboxDeliveryResult(
        UUID outboxId,
        UUID actionId,
        AccountActionPurpose purpose,
        int attempt,
        Status status,
        String failureCode,
        boolean transitioned
) {

    public enum Status {
        SENT,
        FAILED,
        OBSOLETE,
        SKIPPED
    }
}
