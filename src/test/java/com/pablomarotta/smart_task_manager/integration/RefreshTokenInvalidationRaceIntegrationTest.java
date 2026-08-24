package com.pablomarotta.smart_task_manager.integration;

import com.pablomarotta.smart_task_manager.dto.UserRequest;
import com.pablomarotta.smart_task_manager.model.User;
import com.pablomarotta.smart_task_manager.model.AccountActionPurpose;
import com.pablomarotta.smart_task_manager.model.AccountActionRequest;
import com.pablomarotta.smart_task_manager.repository.AccountActionRequestRepository;
import com.pablomarotta.smart_task_manager.repository.UserRepository;
import com.pablomarotta.smart_task_manager.security.ActionTokenCodec;
import com.pablomarotta.smart_task_manager.service.AccountActionService;
import com.pablomarotta.smart_task_manager.service.RefreshTokenService;
import com.pablomarotta.smart_task_manager.service.UserService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.core.AuthenticationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.UUID;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "email-outbox.from-address=test@smart-task-manager.local")
class RefreshTokenInvalidationRaceIntegrationTest extends PostgresIntegrationTest {

    @MockBean
    private JavaMailSender mailSender;

    private final EntityManager entityManager;
    private final AccountActionRequestRepository accountActionRequestRepository;
    private final AccountActionService accountActionService;
    private final ActionTokenCodec actionTokenCodec;
    private final RefreshTokenService refreshTokenService;
    private final PlatformTransactionManager transactionManager;
    private final UserRepository userRepository;
    private final UserService userService;

    @Autowired
    RefreshTokenInvalidationRaceIntegrationTest(
            DataSource dataSource,
            EntityManager entityManager,
            AccountActionRequestRepository accountActionRequestRepository,
            AccountActionService accountActionService,
            ActionTokenCodec actionTokenCodec,
            RefreshTokenService refreshTokenService,
            PlatformTransactionManager transactionManager,
            UserRepository userRepository,
            UserService userService
    ) {
        super(dataSource);
        this.entityManager = entityManager;
        this.accountActionRequestRepository = accountActionRequestRepository;
        this.accountActionService = accountActionService;
        this.actionTokenCodec = actionTokenCodec;
        this.refreshTokenService = refreshTokenService;
        this.transactionManager = transactionManager;
        this.userRepository = userRepository;
        this.userService = userService;
    }

    @Test
    void passwordChangePreventsARacingRefreshTokenFromSurviving() throws Exception {
        assertNoRefreshSurvivorAfterInvalidation(username -> userService.updateUser(username, passwordChangeRequest(username)));
    }

    @Test
    void deactivationPreventsARacingRefreshTokenFromSurviving() throws Exception {
        assertNoRefreshSurvivorAfterInvalidation(userService::deleteUser);
    }

    @Test
    void passwordResetPreventsARacingRefreshTokenFromSurviving() throws Exception {
        User user = saveUser();
        RefreshTokenService.IssuedRefreshToken original = refreshTokenService.issueForUsername(user.getUsername());
        accountActionService.requestPasswordReset(user.getEmail());
        AccountActionRequest action = accountActionRequestRepository.findAll().stream()
                .filter(candidate -> candidate.getPurpose() == AccountActionPurpose.RESET_PASSWORD)
                .filter(candidate -> candidate.getUserId().equals(user.getId()))
                .findFirst()
                .orElseThrow();
        String resetToken = actionTokenCodec.encode(
                action.getId(),
                action.getPurpose(),
                action.getTokenVersion(),
                action.getIssuedAt().toInstant(ZoneOffset.UTC),
                action.getExpiresAt().toInstant(ZoneOffset.UTC)
        );
        CountDownLatch userLockHeld = new CountDownLatch(1);
        CountDownLatch releaseUserLock = new CountDownLatch(1);
        CountDownLatch rotationFinished = new CountDownLatch(1);
        AtomicReference<Throwable> rotationFailure = new AtomicReference<>();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        Future<?> reset = executor.submit(() -> transaction.executeWithoutResult(status -> {
            entityManager.find(User.class, user.getId(), LockModeType.PESSIMISTIC_WRITE);
            userLockHeld.countDown();
            await(releaseUserLock);
            accountActionService.confirmPasswordReset(resetToken, "changed-password");
        }));

        try {
            assertThat(userLockHeld.await(5, TimeUnit.SECONDS)).isTrue();
            Future<?> rotation = executor.submit(() -> {
                try {
                    refreshTokenService.rotate(original.value());
                } catch (Throwable throwable) {
                    rotationFailure.set(throwable);
                } finally {
                    rotationFinished.countDown();
                }
            });

            assertThat(rotationFinished.await(300, TimeUnit.MILLISECONDS)).isFalse();
            releaseUserLock.countDown();
            reset.get(5, TimeUnit.SECONDS);
            rotation.get(5, TimeUnit.SECONDS);

            assertThat(rotationFailure.get()).isInstanceOf(AuthenticationException.class);
            assertThatThrownBy(() -> refreshTokenService.rotate(original.value()))
                    .isInstanceOf(AuthenticationException.class);
        } finally {
            releaseUserLock.countDown();
            reset.get(5, TimeUnit.SECONDS);
            executor.shutdownNow();
        }
    }

    private void assertNoRefreshSurvivorAfterInvalidation(Invalidation invalidation) throws Exception {
        User user = saveUser();
        RefreshTokenService.IssuedRefreshToken original = refreshTokenService.issueForUsername(user.getUsername());
        CountDownLatch userLockHeld = new CountDownLatch(1);
        CountDownLatch allowInvalidation = new CountDownLatch(1);
        CountDownLatch rotationStarted = new CountDownLatch(1);
        CountDownLatch rotationFinished = new CountDownLatch(1);
        AtomicReference<String> replacement = new AtomicReference<>();
        AtomicReference<Throwable> rotationFailure = new AtomicReference<>();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        Future<?> invalidationFuture = executor.submit(() -> transaction.executeWithoutResult(status -> {
            entityManager.find(User.class, user.getId(), LockModeType.PESSIMISTIC_WRITE);
            userLockHeld.countDown();
            await(allowInvalidation);
            invalidation.apply(user.getUsername());
        }));

        try {
            assertThat(userLockHeld.await(5, TimeUnit.SECONDS)).isTrue();
            Future<?> rotationFuture = executor.submit(() -> {
                rotationStarted.countDown();
                try {
                    replacement.set(refreshTokenService.rotate(original.value()).value());
                } catch (Throwable throwable) {
                    rotationFailure.set(throwable);
                } finally {
                    rotationFinished.countDown();
                }
            });

            assertThat(rotationStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(rotationFinished.await(300, TimeUnit.MILLISECONDS))
                    .as("refresh rotation must wait for the invalidating user-row transaction")
                    .isFalse();

            allowInvalidation.countDown();
            invalidationFuture.get(5, TimeUnit.SECONDS);
            rotationFuture.get(5, TimeUnit.SECONDS);

            assertThat(replacement.get()).isNull();
            assertThat(rotationFailure.get()).isInstanceOf(AuthenticationException.class);
            assertThatThrownBy(() -> refreshTokenService.rotate(original.value()))
                    .isInstanceOf(AuthenticationException.class);
        } finally {
            allowInvalidation.countDown();
            invalidationFuture.get(5, TimeUnit.SECONDS);
            executor.shutdownNow();
        }
    }

    private User saveUser() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String username = "race" + suffix.substring(0, 12);
        String email = username + "@example.com";
        return userRepository.saveAndFlush(User.builder()
                .username(username)
                .email(email)
                .emailNormalized(email)
                .password("encoded-password")
                .fullName("Refresh Race User")
                .build());
    }

    private UserRequest passwordChangeRequest(String username) {
        UserRequest request = new UserRequest();
        request.setUsername(username);
        request.setEmail(username + "@example.com");
        request.setFullName("Refresh Race User");
        request.setPassword("changed-password");
        return request;
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for concurrent transaction");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for concurrent transaction", exception);
        }
    }

    @FunctionalInterface
    private interface Invalidation {
        void apply(String username);
    }
}
