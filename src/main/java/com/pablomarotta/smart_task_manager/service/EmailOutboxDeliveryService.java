package com.pablomarotta.smart_task_manager.service;

import com.pablomarotta.smart_task_manager.config.EmailOutboxProperties;
import com.pablomarotta.smart_task_manager.email.EmailDelivery;
import com.pablomarotta.smart_task_manager.email.EmailDeliveryException;
import com.pablomarotta.smart_task_manager.model.AccountActionPurpose;
import com.pablomarotta.smart_task_manager.model.AccountActionRequest;
import com.pablomarotta.smart_task_manager.model.AccountActionState;
import com.pablomarotta.smart_task_manager.model.EmailOutbox;
import com.pablomarotta.smart_task_manager.model.EmailOutboxState;
import com.pablomarotta.smart_task_manager.model.User;
import com.pablomarotta.smart_task_manager.repository.EmailOutboxRepository;
import com.pablomarotta.smart_task_manager.repository.UserRepository;
import com.pablomarotta.smart_task_manager.security.ActionTokenCodec;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

@Service
public class EmailOutboxDeliveryService {

    private static final String DELIVERY_FAILED = "DELIVERY_FAILED";

    private final ActionTokenCodec actionTokenCodec;
    private final Clock clock;
    private final EmailDelivery emailDelivery;
    private final EmailOutboxProperties properties;
    private final EmailOutboxRepository emailOutboxRepository;
    private final EmailOutboxTransitionService transitionService;
    private final EntityManager entityManager;
    private final UserRepository userRepository;

    public EmailOutboxDeliveryService(
            ActionTokenCodec actionTokenCodec,
            Clock clock,
            EmailDelivery emailDelivery,
            EmailOutboxProperties properties,
            EmailOutboxRepository emailOutboxRepository,
            EmailOutboxTransitionService transitionService,
            EntityManager entityManager,
            UserRepository userRepository
    ) {
        this.actionTokenCodec = actionTokenCodec;
        this.clock = clock;
        this.emailDelivery = emailDelivery;
        this.properties = properties;
        this.emailOutboxRepository = emailOutboxRepository;
        this.transitionService = transitionService;
        this.entityManager = entityManager;
        this.userRepository = userRepository;
    }

    /**
     * Holds the user, action and outbox locks across the bounded SMTP call. This intentionally
     * serializes consumption/invalidation with delivery on every application instance; validated
     * combined SMTP connect, read and write budget is strictly shorter than the stale-claim lease.
     */
    @Transactional
    public EmailOutboxDeliveryResult deliver(OutboxClaim claim) {
        Optional<EmailOutboxRepository.DeliveryClaimIdentity> identity = findClaimIdentity(claim);
        if (identity.isEmpty()) {
            return skipped(claim);
        }
        Optional<User> user = userRepository.findActiveForUpdateById(identity.get().getUserId());
        if (user.isEmpty()) {
            return obsolete(claim, identity.get().getActionId(), null);
        }
        AccountActionRequest action = entityManager.find(
                AccountActionRequest.class,
                identity.get().getActionId(),
                LockModeType.PESSIMISTIC_WRITE
        );
        Optional<EmailOutbox> outbox = findCurrentClaim(claim);
        if (outbox.isEmpty()) {
            return skipped(claim);
        }
        if (!isDeliverable(outbox.get(), action, user.get().getId())) {
            return obsolete(claim, identity.get().getActionId(), outbox.get().getPurpose());
        }
        return send(claim, outbox.get(), action, user.get());
    }

    private Optional<EmailOutboxRepository.DeliveryClaimIdentity> findClaimIdentity(OutboxClaim claim) {
        return emailOutboxRepository.findDeliveryClaimIdentity(
                claim.outboxId(), EmailOutboxState.PROCESSING, claim.claimedAt(), claim.attempt()
        );
    }

    private Optional<EmailOutbox> findCurrentClaim(OutboxClaim claim) {
        return emailOutboxRepository.findCurrentClaimForUpdate(
                claim.outboxId(), EmailOutboxState.PROCESSING, claim.claimedAt(), claim.attempt()
        );
    }

    private boolean isDeliverable(EmailOutbox outbox, AccountActionRequest action, Long userId) {
        return action != null
                && userId.equals(action.getUserId())
                && outbox.getPurpose() == action.getPurpose()
                && action.getState() == AccountActionState.PENDING
                && action.getExpiresAt().isAfter(now());
    }

    private EmailOutboxDeliveryResult send(
            OutboxClaim claim,
            EmailOutbox outbox,
            AccountActionRequest action,
            User user
    ) {
        try {
            emailDelivery.deliver(messageFor(action, user));
            boolean transitioned = transitionService.markSent(claim);
            return result(claim, action, EmailOutboxDeliveryResult.Status.SENT, null, transitioned);
        } catch (EmailDeliveryException exception) {
            String failureCode = stableFailureCode(exception.failureCode());
            boolean transitioned = transitionService.markFailure(claim, failureCode);
            return result(claim, action, EmailOutboxDeliveryResult.Status.FAILED, failureCode, transitioned);
        } catch (RuntimeException exception) {
            boolean transitioned = transitionService.markFailure(claim, DELIVERY_FAILED);
            return result(claim, action, EmailOutboxDeliveryResult.Status.FAILED, DELIVERY_FAILED, transitioned);
        }
    }

    private EmailDelivery.Message messageFor(AccountActionRequest action, User user) {
        String compactToken = actionTokenCodec.encode(
                action.getId(),
                action.getPurpose(),
                action.getTokenVersion(),
                action.getIssuedAt().toInstant(ZoneOffset.UTC),
                action.getExpiresAt().toInstant(ZoneOffset.UTC)
        );
        String link = normalizedBaseUrl() + pageFor(action) + "#token=" + compactToken;
        return new EmailDelivery.Message(user.getEmail(), subjectFor(action), "Open this link to continue:\n" + link);
    }

    private String normalizedBaseUrl() {
        String configured = properties.getLinkBaseUrl().trim();
        return configured.endsWith("/") ? configured.substring(0, configured.length() - 1) : configured;
    }

    private String pageFor(AccountActionRequest action) {
        return action.getPurpose() == AccountActionPurpose.VERIFY_EMAIL
                ? "/verify-email" : "/reset-password";
    }

    private String subjectFor(AccountActionRequest action) {
        return action.getPurpose() == AccountActionPurpose.VERIFY_EMAIL
                ? "Verify your Smart Task Manager email" : "Reset your Smart Task Manager password";
    }

    private EmailOutboxDeliveryResult obsolete(
            OutboxClaim claim,
            UUID actionId,
            AccountActionPurpose purpose
    ) {
        boolean transitioned = transitionService.markObsolete(claim);
        return new EmailOutboxDeliveryResult(
                claim.outboxId(), actionId, purpose, claim.attempt(),
                EmailOutboxDeliveryResult.Status.OBSOLETE, null, transitioned
        );
    }

    private EmailOutboxDeliveryResult skipped(OutboxClaim claim) {
        return new EmailOutboxDeliveryResult(
                claim.outboxId(), null, null, claim.attempt(),
                EmailOutboxDeliveryResult.Status.SKIPPED, null, false
        );
    }

    private EmailOutboxDeliveryResult result(
            OutboxClaim claim,
            AccountActionRequest action,
            EmailOutboxDeliveryResult.Status status,
            String failureCode,
            boolean transitioned
    ) {
        return new EmailOutboxDeliveryResult(
                claim.outboxId(), action.getId(), action.getPurpose(), claim.attempt(), status, failureCode, transitioned
        );
    }

    private String stableFailureCode(String failureCode) {
        return failureCode != null && failureCode.matches("[A-Z_]{1,64}") ? failureCode : DELIVERY_FAILED;
    }

    private LocalDateTime now() {
        Instant instant = clock.instant();
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
