package com.pablomarotta.smart_task_manager.integration;

import com.pablomarotta.smart_task_manager.dto.UserRequest;
import com.pablomarotta.smart_task_manager.model.AccountActionPurpose;
import com.pablomarotta.smart_task_manager.model.AccountActionRequest;
import com.pablomarotta.smart_task_manager.model.User;
import com.pablomarotta.smart_task_manager.repository.AccountActionRequestRepository;
import com.pablomarotta.smart_task_manager.repository.UserRepository;
import com.pablomarotta.smart_task_manager.security.ActionTokenCodec;
import com.pablomarotta.smart_task_manager.service.AccountActionService;
import com.pablomarotta.smart_task_manager.service.RefreshTokenService;
import com.pablomarotta.smart_task_manager.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
@SpringBootTest
class UserProfileSecurityRaceIntegrationTest extends PostgresIntegrationTest {

    private final AccountActionRequestRepository accountActionRequestRepository;
    private final AccountActionService accountActionService;
    private final ActionTokenCodec actionTokenCodec;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final PlatformTransactionManager transactionManager;
    private final UserRepository userRepository;
    private final UserService userService;

    @Autowired
    UserProfileSecurityRaceIntegrationTest(
            DataSource dataSource,
            AccountActionRequestRepository accountActionRequestRepository,
            AccountActionService accountActionService,
            ActionTokenCodec actionTokenCodec,
            PasswordEncoder passwordEncoder,
            RefreshTokenService refreshTokenService,
            PlatformTransactionManager transactionManager,
            UserRepository userRepository,
            UserService userService
    ) {
        super(dataSource);
        this.accountActionRequestRepository = accountActionRequestRepository;
        this.accountActionService = accountActionService;
        this.actionTokenCodec = actionTokenCodec;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
        this.transactionManager = transactionManager;
        this.userRepository = userRepository;
        this.userService = userService;
    }

    @Test
    void profileUpdateSerializesWithPasswordResetAndCannotRestoreStaleSecurityFields() throws Exception {
        User user = saveUser();
        RefreshTokenService.IssuedRefreshToken originalRefresh = refreshTokenService.issueForUsername(user.getUsername());
        String resetToken = issueResetToken(user);
        CountDownLatch profileLockHeld = new CountDownLatch(1);
        CountDownLatch releaseProfile = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<?> profileUpdate = executor.submit(
                () -> updateProfileWhileHoldingUserLock(user.getUsername(), profileLockHeld, releaseProfile)
        );

        try {
            assertThat(profileLockHeld.await(5, TimeUnit.SECONDS)).isTrue();
            CountDownLatch resetStarted = new CountDownLatch(1);
            CountDownLatch resetFinished = new CountDownLatch(1);
            Future<?> reset = executor.submit(() -> {
                resetStarted.countDown();
                try {
                    accountActionService.confirmPasswordReset(resetToken, "changed-password");
                } finally {
                    resetFinished.countDown();
                }
            });
            assertThat(resetStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(resetFinished.await(300, TimeUnit.MILLISECONDS))
                    .as("password reset must wait for the profile user-row lock")
                    .isFalse();

            releaseProfile.countDown();
            profileUpdate.get(5, TimeUnit.SECONDS);
            reset.get(5, TimeUnit.SECONDS);

            User persisted = userRepository.findById(user.getId()).orElseThrow();
            assertThat(persisted.getActive()).isTrue();
            assertThat(persisted.getAuthVersion()).isEqualTo(1);
            assertThat(passwordEncoder.matches("changed-password", persisted.getPassword())).isTrue();
            assertThatThrownBy(() -> refreshTokenService.rotate(originalRefresh.value()))
                    .isInstanceOf(AuthenticationException.class);
        } finally {
            releaseProfile.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void profileUpdateSerializesWithDeactivationAndCannotResurrectTheAccount() throws Exception {
        User user = saveUser();
        RefreshTokenService.IssuedRefreshToken originalRefresh = refreshTokenService.issueForUsername(user.getUsername());
        CountDownLatch profileLockHeld = new CountDownLatch(1);
        CountDownLatch releaseProfile = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<?> profileUpdate = executor.submit(
                () -> updateProfileWhileHoldingUserLock(user.getUsername(), profileLockHeld, releaseProfile)
        );

        try {
            assertThat(profileLockHeld.await(5, TimeUnit.SECONDS)).isTrue();
            CountDownLatch deactivationStarted = new CountDownLatch(1);
            CountDownLatch deactivationFinished = new CountDownLatch(1);
            Future<?> deactivation = executor.submit(() -> {
                deactivationStarted.countDown();
                try {
                    userService.deleteUser(user.getUsername());
                } finally {
                    deactivationFinished.countDown();
                }
            });
            assertThat(deactivationStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(deactivationFinished.await(300, TimeUnit.MILLISECONDS))
                    .as("deactivation must wait for the profile user-row lock")
                    .isFalse();

            releaseProfile.countDown();
            profileUpdate.get(5, TimeUnit.SECONDS);
            deactivation.get(5, TimeUnit.SECONDS);

            User persisted = userRepository.findById(user.getId()).orElseThrow();
            assertThat(persisted.getActive()).isFalse();
            assertThat(persisted.getAuthVersion()).isEqualTo(1);
            assertThatThrownBy(() -> refreshTokenService.rotate(originalRefresh.value()))
                    .isInstanceOf(AuthenticationException.class);
        } finally {
            releaseProfile.countDown();
            executor.shutdownNow();
        }
    }

    private void updateProfileWhileHoldingUserLock(
            String username,
            CountDownLatch profileLockHeld,
            CountDownLatch releaseProfile
    ) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            User user = userRepository.findActiveForUpdateByUsername(username).orElseThrow();
            profileLockHeld.countDown();
            await(releaseProfile);
            user.setFullName("Updated Profile Name");
            userRepository.save(user);
        });
    }

    private String issueResetToken(User user) {
        accountActionService.requestPasswordReset(user.getEmail());
        AccountActionRequest action = accountActionRequestRepository.findAll().stream()
                .filter(candidate -> candidate.getUserId().equals(user.getId()))
                .filter(candidate -> candidate.getPurpose() == AccountActionPurpose.RESET_PASSWORD)
                .findFirst()
                .orElseThrow();
        return actionTokenCodec.encode(
                action.getId(),
                action.getPurpose(),
                action.getTokenVersion(),
                action.getIssuedAt().toInstant(ZoneOffset.UTC),
                action.getExpiresAt().toInstant(ZoneOffset.UTC)
        );
    }

    private User saveUser() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String username = "profile" + suffix.substring(0, 12);
        String email = username + "@example.com";
        return userRepository.saveAndFlush(User.builder()
                .username(username)
                .email(email)
                .emailNormalized(email)
                .password(passwordEncoder.encode("original-password"))
                .fullName("Profile Race User")
                .build());
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
}
