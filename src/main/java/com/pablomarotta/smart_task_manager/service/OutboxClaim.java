package com.pablomarotta.smart_task_manager.service;

import java.time.LocalDateTime;
import java.util.UUID;

public record OutboxClaim(UUID outboxId, LocalDateTime claimedAt, int attempt) {
}
