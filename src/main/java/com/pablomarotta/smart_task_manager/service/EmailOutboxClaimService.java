package com.pablomarotta.smart_task_manager.service;

import com.pablomarotta.smart_task_manager.config.EmailOutboxProperties;
import com.pablomarotta.smart_task_manager.model.EmailOutbox;
import com.pablomarotta.smart_task_manager.model.EmailOutboxState;
import com.pablomarotta.smart_task_manager.repository.EmailOutboxRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Service
public class EmailOutboxClaimService {

    private static final String STALE_CLAIM_FAILURE_CODE = "DELIVERY_CLAIM_EXPIRED";

    private final EmailOutboxRepository emailOutboxRepository;
    private final Clock clock;
    private final EmailOutboxProperties properties;

    public EmailOutboxClaimService(
            EmailOutboxRepository emailOutboxRepository,
            Clock clock,
            EmailOutboxProperties properties
    ) {
        this.emailOutboxRepository = emailOutboxRepository;
        this.clock = clock;
        this.properties = properties;
    }

    @Transactional
    public List<OutboxClaim> claimDue() {
        LocalDateTime now = now();
        List<EmailOutbox> candidates = emailOutboxRepository.findClaimableForUpdate(
                now,
                now.minus(properties.getStaleClaimAfter()),
                properties.getBatchSize()
        );
        List<OutboxClaim> claims = new ArrayList<>(candidates.size());
        for (EmailOutbox candidate : candidates) {
            if (candidate.getState() == EmailOutboxState.PROCESSING
                    && candidate.getAttempts() >= properties.getMaximumAttempts()) {
                candidate.setState(EmailOutboxState.DEAD);
                candidate.setSentAt(null);
                candidate.setLastErrorCode(STALE_CLAIM_FAILURE_CODE);
                continue;
            }
            int attempt = candidate.getAttempts() + 1;
            candidate.setAttempts(attempt);
            candidate.setState(EmailOutboxState.PROCESSING);
            candidate.setClaimedAt(now);
            candidate.setSentAt(null);
            candidate.setLastErrorCode(null);
            claims.add(new OutboxClaim(candidate.getId(), now, attempt));
        }
        emailOutboxRepository.flush();
        return claims;
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
