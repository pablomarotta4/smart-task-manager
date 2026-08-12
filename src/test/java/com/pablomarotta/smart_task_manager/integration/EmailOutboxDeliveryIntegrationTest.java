package com.pablomarotta.smart_task_manager.integration;

import com.pablomarotta.smart_task_manager.email.EmailDelivery;
import com.pablomarotta.smart_task_manager.email.EmailOutboxDispatcher;
import com.pablomarotta.smart_task_manager.config.EmailOutboxProperties;
import com.pablomarotta.smart_task_manager.model.AccountActionPurpose;
import com.pablomarotta.smart_task_manager.model.AccountActionRequest;
import com.pablomarotta.smart_task_manager.model.AccountActionState;
import com.pablomarotta.smart_task_manager.model.EmailOutbox;
import com.pablomarotta.smart_task_manager.model.EmailOutboxKind;
import com.pablomarotta.smart_task_manager.model.EmailOutboxState;
import com.pablomarotta.smart_task_manager.model.User;
import com.pablomarotta.smart_task_manager.repository.AccountActionRequestRepository;
import com.pablomarotta.smart_task_manager.repository.EmailOutboxRepository;
import com.pablomarotta.smart_task_manager.repository.UserRepository;
import com.pablomarotta.smart_task_manager.service.EmailOutboxClaimService;
import com.pablomarotta.smart_task_manager.service.EmailOutboxTransitionService;
import com.pablomarotta.smart_task_manager.service.OutboxClaim;
import com.pablomarotta.smart_task_manager.service.AccountActionService;
import com.pablomarotta.smart_task_manager.security.ActionTokenCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;

@SpringBootTest
class EmailOutboxDeliveryIntegrationTest extends PostgresIntegrationTest {

    @MockBean
    private EmailDelivery emailDelivery;

    private final AccountActionRequestRepository accountActionRequestRepository;
    private final AccountActionService accountActionService;
    private final ActionTokenCodec actionTokenCodec;
    private final EmailOutboxClaimService claimService;
    private final EmailOutboxDispatcher dispatcher;
    private final EmailOutboxRepository emailOutboxRepository;
    private final EmailOutboxProperties emailOutboxProperties;
    private final EmailOutboxTransitionService transitionService;
    private final UserRepository userRepository;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    EmailOutboxDeliveryIntegrationTest(
            DataSource dataSource,
            AccountActionRequestRepository accountActionRequestRepository,
            AccountActionService accountActionService,
            ActionTokenCodec actionTokenCodec,
            EmailOutboxClaimService claimService,
            EmailOutboxDispatcher dispatcher,
            EmailOutboxRepository emailOutboxRepository,
            EmailOutboxProperties emailOutboxProperties,
            EmailOutboxTransitionService transitionService,
            UserRepository userRepository,
            JdbcTemplate jdbcTemplate
    ) {
        super(dataSource);
        this.accountActionRequestRepository = accountActionRequestRepository;
        this.accountActionService = accountActionService;
        this.actionTokenCodec = actionTokenCodec;
        this.claimService = claimService;
        this.dispatcher = dispatcher;
        this.emailOutboxRepository = emailOutboxRepository;
        this.emailOutboxProperties = emailOutboxProperties;
        this.transitionService = transitionService;
        this.userRepository = userRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void cleanOutboxFixtures() {
        jdbcTemplate.update("DELETE FROM email_outbox WHERE recipient_user_id IN (SELECT id FROM users WHERE username LIKE 'outbox%')");
        jdbcTemplate.update("DELETE FROM account_action_requests WHERE user_id IN (SELECT id FROM users WHERE username LIKE 'outbox%')");
        jdbcTemplate.update("DELETE FROM users WHERE username LIKE 'outbox%'");
    }

    @Test
    void recoversAStaleProcessingClaimWithANewerAttempt() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC).withNano(0);
        EmailOutbox outbox = saveOutbox(AccountActionState.PENDING, EmailOutboxState.PROCESSING, 1,
                now.minusMinutes(20), now.minusMinutes(10), now.plusMinutes(30));

        List<OutboxClaim> claims = claimService.claimDue();

        OutboxClaim recovered = claims.stream()
                .filter(claim -> claim.outboxId().equals(outbox.getId()))
                .findFirst()
                .orElseThrow();
        EmailOutbox persisted = emailOutboxRepository.findById(outbox.getId()).orElseThrow();
        assertThat(recovered.attempt()).isEqualTo(2);
        assertThat(persisted.getState()).isEqualTo(EmailOutboxState.PROCESSING);
        assertThat(persisted.getClaimedAt()).isEqualTo(recovered.claimedAt());
        assertThat(persisted.getLastErrorCode()).isNull();
    }

    @Test
    void concurrentClaimersDoNotDuplicateDueRows() throws Exception {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC).withNano(0);
        EmailOutbox first = saveOutbox(AccountActionState.PENDING, EmailOutboxState.PENDING, 0,
                now.minusMinutes(1), null, now.plusMinutes(30));
        EmailOutbox second = saveOutbox(AccountActionState.PENDING, EmailOutboxState.PENDING, 0,
                now.minusMinutes(1), null, now.plusMinutes(30));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        int originalBatchSize = emailOutboxProperties.getBatchSize();
        try {
            emailOutboxProperties.setBatchSize(1);
            Callable<List<OutboxClaim>> claimDue = claimService::claimDue;
            Future<List<OutboxClaim>> firstWorker = executor.submit(claimDue);
            Future<List<OutboxClaim>> secondWorker = executor.submit(claimDue);

            Set<UUID> firstClaimed = firstWorker.get().stream().map(OutboxClaim::outboxId).collect(java.util.stream.Collectors.toSet());
            Set<UUID> secondClaimed = secondWorker.get().stream().map(OutboxClaim::outboxId).collect(java.util.stream.Collectors.toSet());
            Set<UUID> allClaimed = new java.util.HashSet<>(firstClaimed);
            allClaimed.addAll(secondClaimed);

            assertThat(firstClaimed).hasSize(1);
            assertThat(secondClaimed).hasSize(1);
            assertThat(java.util.Collections.disjoint(firstClaimed, secondClaimed)).isTrue();
            assertThat(allClaimed).contains(first.getId(), second.getId());
        } finally {
            emailOutboxProperties.setBatchSize(originalBatchSize);
            executor.shutdownNow();
        }
    }

    @Test
    void successfulDeliveryTransitionsClaimToSent() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC).withNano(0);
        EmailOutbox outbox = saveOutbox(AccountActionState.PENDING, EmailOutboxState.PENDING, 0,
                now.minusMinutes(1), null, now.plusMinutes(30));

        withDispatcherEnabled(dispatcher::dispatchDueEmails);

        EmailOutbox persisted = emailOutboxRepository.findById(outbox.getId()).orElseThrow();
        assertThat(persisted.getState()).isEqualTo(EmailOutboxState.SENT);
        assertThat(persisted.getSentAt()).isNotNull();
        assertThat(persisted.getLastErrorCode()).isNull();
    }

    @Test
    void terminalDeliveryFailureTransitionsClaimToDeadWithOnlyAStableCode() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC).withNano(0);
        EmailOutbox outbox = saveOutbox(AccountActionState.PENDING, EmailOutboxState.PENDING, 0,
                now.minusMinutes(1), null, now.plusMinutes(30));
        int originalMaximumAttempts = emailOutboxProperties.getMaximumAttempts();
        emailOutboxProperties.setMaximumAttempts(1);
        doThrow(new com.pablomarotta.smart_task_manager.email.EmailDeliveryException("SMTP_DELIVERY_FAILED"))
                .when(emailDelivery).deliver(org.mockito.ArgumentMatchers.any());
        try {
            withDispatcherEnabled(dispatcher::dispatchDueEmails);
        } finally {
            emailOutboxProperties.setMaximumAttempts(originalMaximumAttempts);
        }

        EmailOutbox persisted = emailOutboxRepository.findById(outbox.getId()).orElseThrow();
        assertThat(persisted.getState()).isEqualTo(EmailOutboxState.DEAD);
        assertThat(persisted.getSentAt()).isNull();
        assertThat(persisted.getLastErrorCode()).isEqualTo("SMTP_DELIVERY_FAILED");
    }

    @Test
    void staleWorkerCannotCompleteANewerClaim() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC).withNano(0);
        EmailOutbox outbox = saveOutbox(AccountActionState.PENDING, EmailOutboxState.PROCESSING, 1,
                now.minusMinutes(20), now.minusMinutes(10), now.plusMinutes(30));
        OutboxClaim staleClaim = new OutboxClaim(outbox.getId(), now.minusMinutes(10), 1);
        OutboxClaim newerClaim = claimService.claimDue().stream()
                .filter(claim -> claim.outboxId().equals(outbox.getId()))
                .findFirst()
                .orElseThrow();

        boolean staleTransitioned = transitionService.markSent(staleClaim);

        EmailOutbox persisted = emailOutboxRepository.findById(outbox.getId()).orElseThrow();
        assertThat(staleTransitioned).isFalse();
        assertThat(persisted.getState()).isEqualTo(EmailOutboxState.PROCESSING);
        assertThat(persisted.getClaimedAt()).isEqualTo(newerClaim.claimedAt());
        assertThat(persisted.getAttempts()).isEqualTo(newerClaim.attempt());
    }

    @Test
    void invalidatedActionOutboxNeverInvokesDeliveryAndBecomesDead() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC).withNano(0);
        EmailOutbox outbox = saveOutbox(AccountActionState.INVALIDATED, EmailOutboxState.PENDING, 0,
                now.minusMinutes(1), null, now.plusMinutes(30));

        withDispatcherEnabled(dispatcher::dispatchDueEmails);

        EmailOutbox persisted = emailOutboxRepository.findById(outbox.getId()).orElseThrow();
        verifyNoInteractions(emailDelivery);
        assertThat(persisted.getState()).isEqualTo(EmailOutboxState.DEAD);
        assertThat(persisted.getLastErrorCode()).isEqualTo("ACTION_NOT_DELIVERABLE");
        assertThat(persisted.getSentAt()).isNull();
    }

    @Test
    void consumptionWaitsUntilAnInFlightDeliveryHasCrossedTheDatabaseFence() throws Exception {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC).withNano(0);
        DeliverableOutbox deliverable = saveDeliverableVerificationOutbox(now);
        CountDownLatch deliveryStarted = new CountDownLatch(1);
        CountDownLatch releaseDelivery = new CountDownLatch(1);
        org.mockito.Mockito.doAnswer(invocation -> {
            deliveryStarted.countDown();
            assertThat(releaseDelivery.await(5, TimeUnit.SECONDS)).isTrue();
            return null;
        }).when(emailDelivery).deliver(org.mockito.ArgumentMatchers.any());
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> dispatched = executor.submit(() -> withDispatcherEnabled(dispatcher::dispatchDueEmails));
            assertThat(deliveryStarted.await(5, TimeUnit.SECONDS)).isTrue();

            Future<?> consumed = executor.submit(
                    () -> accountActionService.confirmEmailVerification(deliverable.compactToken())
            );

            assertThatThrownBy(() -> consumed.get(250, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);

            releaseDelivery.countDown();
            dispatched.get(5, TimeUnit.SECONDS);
            consumed.get(5, TimeUnit.SECONDS);

            assertThat(accountActionRequestRepository.findById(deliverable.outbox().getAccountActionRequest().getId())
                    .orElseThrow().getState()).isEqualTo(AccountActionState.CONSUMED);
            assertThat(emailOutboxRepository.findById(deliverable.outbox().getId()).orElseThrow().getState())
                    .isEqualTo(EmailOutboxState.SENT);
        } finally {
            releaseDelivery.countDown();
            executor.shutdownNow();
        }
    }

    private EmailOutbox saveOutbox(
            AccountActionState actionState,
            EmailOutboxState outboxState,
            int attempts,
            LocalDateTime availableAt,
            LocalDateTime claimedAt,
            LocalDateTime expiresAt
    ) {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        User user = userRepository.saveAndFlush(User.builder()
                .username("outbox" + suffix.substring(0, 12))
                .email("outbox" + suffix + "@example.com")
                .emailNormalized("outbox" + suffix + "@example.com")
                .password("encoded-password")
                .fullName("Outbox Delivery User")
                .build());
        LocalDateTime issuedAt = availableAt.minusMinutes(1);
        AccountActionRequest action = accountActionRequestRepository.saveAndFlush(AccountActionRequest.builder()
                .id(UUID.randomUUID())
                .user(user)
                .purpose(AccountActionPurpose.VERIFY_EMAIL)
                .state(actionState)
                .tokenHash(UUID.randomUUID().toString().replace("-", "").repeat(2))
                .tokenVersion(1)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .invalidatedAt(actionState == AccountActionState.INVALIDATED ? issuedAt : null)
                .build());
        return emailOutboxRepository.saveAndFlush(EmailOutbox.builder()
                .id(UUID.randomUUID())
                .recipient(user)
                .accountActionRequest(action)
                .kind(EmailOutboxKind.ACCOUNT_ACTION)
                .purpose(AccountActionPurpose.VERIFY_EMAIL)
                .state(outboxState)
                .attempts(attempts)
                .availableAt(availableAt)
                .claimedAt(claimedAt)
                .build());
    }

    private DeliverableOutbox saveDeliverableVerificationOutbox(LocalDateTime now) {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        User user = userRepository.saveAndFlush(User.builder()
                .username("outbox" + suffix.substring(0, 12))
                .email("outbox" + suffix + "@example.com")
                .emailNormalized("outbox" + suffix + "@example.com")
                .password("encoded-password")
                .fullName("Outbox Delivery User")
                .build());
        UUID actionId = UUID.randomUUID();
        LocalDateTime issuedAt = now.minusMinutes(1);
        LocalDateTime expiresAt = now.plusMinutes(30);
        String compactToken = actionTokenCodec.encode(
                actionId,
                AccountActionPurpose.VERIFY_EMAIL,
                1,
                issuedAt.toInstant(ZoneOffset.UTC),
                expiresAt.toInstant(ZoneOffset.UTC)
        );
        AccountActionRequest action = accountActionRequestRepository.saveAndFlush(AccountActionRequest.builder()
                .id(actionId)
                .user(user)
                .purpose(AccountActionPurpose.VERIFY_EMAIL)
                .state(AccountActionState.PENDING)
                .tokenHash(actionTokenCodec.hash(compactToken))
                .tokenVersion(1)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build());
        EmailOutbox outbox = emailOutboxRepository.saveAndFlush(EmailOutbox.builder()
                .id(UUID.randomUUID())
                .recipient(user)
                .accountActionRequest(action)
                .kind(EmailOutboxKind.ACCOUNT_ACTION)
                .purpose(AccountActionPurpose.VERIFY_EMAIL)
                .state(EmailOutboxState.PENDING)
                .attempts(0)
                .availableAt(now.minusMinutes(1))
                .build());
        return new DeliverableOutbox(outbox, compactToken);
    }

    private void withDispatcherEnabled(Runnable dispatch) {
        boolean originallyEnabled = emailOutboxProperties.isEnabled();
        emailOutboxProperties.setEnabled(true);
        try {
            dispatch.run();
        } finally {
            emailOutboxProperties.setEnabled(originallyEnabled);
        }
    }

    private record DeliverableOutbox(EmailOutbox outbox, String compactToken) {
    }
}
