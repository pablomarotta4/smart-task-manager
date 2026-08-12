package com.pablomarotta.smart_task_manager.integration;

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
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AccountActionPersistenceIntegrationTest extends PostgresIntegrationTest {

    private final AccountActionRequestRepository accountActionRequestRepository;
    private final EmailOutboxRepository emailOutboxRepository;
    private final EntityManager entityManager;
    private final UserRepository userRepository;

    @Autowired
    AccountActionPersistenceIntegrationTest(
            DataSource dataSource,
            AccountActionRequestRepository accountActionRequestRepository,
            EmailOutboxRepository emailOutboxRepository,
            EntityManager entityManager,
            UserRepository userRepository
    ) {
        super(dataSource);
        this.accountActionRequestRepository = accountActionRequestRepository;
        this.emailOutboxRepository = emailOutboxRepository;
        this.entityManager = entityManager;
        this.userRepository = userRepository;
    }

    @Test
    @Transactional
    void locksAnActionByIdAndTokenHashThenInvalidatesOnlyTheCurrentPendingAction() {
        User user = saveUser();
        LocalDateTime issuedAt = LocalDateTime.of(2026, 8, 12, 16, 0);
        AccountActionRequest pendingAction = accountActionRequestRepository.save(AccountActionRequest.builder()
                .id(UUID.randomUUID())
                .user(user)
                .purpose(AccountActionPurpose.VERIFY_EMAIL)
                .state(AccountActionState.PENDING)
                .tokenHash("a".repeat(64))
                .tokenVersion(1)
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusMinutes(30))
                .build());
        AccountActionRequest consumedAction = accountActionRequestRepository.save(AccountActionRequest.builder()
                .id(UUID.randomUUID())
                .user(user)
                .purpose(AccountActionPurpose.RESET_PASSWORD)
                .state(AccountActionState.CONSUMED)
                .tokenHash("b".repeat(64))
                .tokenVersion(1)
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusMinutes(30))
                .consumedAt(issuedAt.plusMinutes(1))
                .build());
        entityManager.flush();
        entityManager.clear();

        AccountActionRequest lockedAction = accountActionRequestRepository
                .findForUpdateByIdAndTokenHash(pendingAction.getId(), "a".repeat(64))
                .orElseThrow();

        assertThat(lockedAction.getId()).isEqualTo(pendingAction.getId());
        assertThat(accountActionRequestRepository.findForUpdateByIdAndTokenHash(
                pendingAction.getId(), "c".repeat(64)
        )).isEmpty();

        int invalidatedRows = accountActionRequestRepository.invalidatePendingByUserIdAndPurpose(
                user.getId(),
                AccountActionPurpose.VERIFY_EMAIL,
                issuedAt.plusMinutes(2)
        );
        entityManager.flush();
        entityManager.clear();

        assertThat(invalidatedRows).isEqualTo(1);
        AccountActionRequest invalidatedAction = accountActionRequestRepository.findById(pendingAction.getId())
                .orElseThrow();
        assertThat(invalidatedAction.getState()).isEqualTo(AccountActionState.INVALIDATED);
        assertThat(invalidatedAction.getInvalidatedAt()).isEqualTo(issuedAt.plusMinutes(2));
        assertThat(invalidatedAction.getUpdatedAt()).isEqualTo(issuedAt.plusMinutes(2));
        assertThat(accountActionRequestRepository.findById(consumedAction.getId()).orElseThrow().getState())
                .isEqualTo(AccountActionState.CONSUMED);
    }

    @Test
    @Transactional
    void persistsOutboxMetadataWithoutPersistingAnAccountActionToken() {
        User user = saveUser();
        LocalDateTime now = LocalDateTime.of(2026, 8, 12, 16, 0);
        AccountActionRequest accountAction = accountActionRequestRepository.save(AccountActionRequest.builder()
                .id(UUID.randomUUID())
                .user(user)
                .purpose(AccountActionPurpose.RESET_PASSWORD)
                .state(AccountActionState.PENDING)
                .tokenHash("d".repeat(64))
                .tokenVersion(1)
                .issuedAt(now)
                .expiresAt(now.plusMinutes(30))
                .build());
        EmailOutbox outbox = emailOutboxRepository.save(EmailOutbox.builder()
                .id(UUID.randomUUID())
                .recipient(user)
                .accountActionRequest(accountAction)
                .kind(EmailOutboxKind.ACCOUNT_ACTION)
                .purpose(AccountActionPurpose.RESET_PASSWORD)
                .state(EmailOutboxState.PENDING)
                .attempts(0)
                .availableAt(now)
                .build());
        entityManager.flush();
        entityManager.clear();

        EmailOutbox persistedOutbox = emailOutboxRepository.findById(outbox.getId()).orElseThrow();

        assertThat(persistedOutbox.getAccountActionRequest().getId()).isEqualTo(accountAction.getId());
        assertThat(persistedOutbox.getState()).isEqualTo(EmailOutboxState.PENDING);
        assertThat(persistedOutbox.getAttempts()).isZero();
    }

    private User saveUser() {
        String uniqueSuffix = UUID.randomUUID().toString().replace("-", "");
        String email = "accountaction" + uniqueSuffix + "@example.com";
        return userRepository.save(User.builder()
                .username("accountaction" + uniqueSuffix.substring(0, 12))
                .email(email)
                .emailNormalized(email)
                .password("encoded")
                .fullName("Account Action User")
                .build());
    }
}
