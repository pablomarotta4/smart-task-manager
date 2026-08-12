package com.pablomarotta.smart_task_manager.service;

import com.pablomarotta.smart_task_manager.config.EmailOutboxProperties;
import com.pablomarotta.smart_task_manager.repository.EmailOutboxRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
public class EmailOutboxTransitionService {

    private static final String ACTION_NOT_DELIVERABLE = "ACTION_NOT_DELIVERABLE";

    private final EmailOutboxRepository emailOutboxRepository;
    private final Clock clock;
    private final EmailOutboxProperties properties;

    public EmailOutboxTransitionService(
            EmailOutboxRepository emailOutboxRepository,
            Clock clock,
            EmailOutboxProperties properties
    ) {
        this.emailOutboxRepository = emailOutboxRepository;
        this.clock = clock;
        this.properties = properties;
    }

    @Transactional
    public boolean markSent(OutboxClaim claim) {
        LocalDateTime now = now();
        return emailOutboxRepository.markSent(
                claim.outboxId(),
                claim.claimedAt(),
                claim.attempt(),
                now,
                now
        ) == 1;
    }

    @Transactional
    public boolean markFailure(OutboxClaim claim, String failureCode) {
        LocalDateTime now = now();
        if (claim.attempt() >= properties.getMaximumAttempts()) {
            return emailOutboxRepository.markDead(
                    claim.outboxId(),
                    claim.claimedAt(),
                    claim.attempt(),
                    failureCode,
                    now
            ) == 1;
        }
        return emailOutboxRepository.releaseForRetry(
                claim.outboxId(),
                claim.claimedAt(),
                claim.attempt(),
                now.plus(retryDelay(claim.attempt())),
                now
        ) == 1;
    }

    @Transactional
    public boolean markObsolete(OutboxClaim claim) {
        return emailOutboxRepository.markDead(
                claim.outboxId(),
                claim.claimedAt(),
                claim.attempt(),
                ACTION_NOT_DELIVERABLE,
                now()
        ) == 1;
    }

    private java.time.Duration retryDelay(int attempt) {
        long initialMillis = properties.getRetryInitialDelay().toMillis();
        long maximumMillis = properties.getRetryMaximumDelay().toMillis();
        int exponent = Math.min(Math.max(attempt - 1, 0), 30);
        long scaledMillis = initialMillis > Long.MAX_VALUE >> exponent
                ? Long.MAX_VALUE
                : initialMillis << exponent;
        return java.time.Duration.ofMillis(Math.min(scaledMillis, maximumMillis));
    }

    private LocalDateTime now() {
        Instant instant = clock.instant();
        int micros = instant.getNano() / 1_000;
        return LocalDateTime.ofInstant(
                Instant.ofEpochSecond(instant.getEpochSecond(), micros * 1_000L),
                ZoneOffset.UTC
        );
    }
}
