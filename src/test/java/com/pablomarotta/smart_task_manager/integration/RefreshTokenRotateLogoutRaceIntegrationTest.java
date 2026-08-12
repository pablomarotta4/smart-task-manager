package com.pablomarotta.smart_task_manager.integration;

import com.pablomarotta.smart_task_manager.model.User;
import com.pablomarotta.smart_task_manager.repository.UserRepository;
import com.pablomarotta.smart_task_manager.service.RefreshTokenService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.AuthenticationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class RefreshTokenRotateLogoutRaceIntegrationTest extends PostgresIntegrationTest {

    private final EntityManager entityManager;
    private final RefreshTokenService refreshTokenService;
    private final PlatformTransactionManager transactionManager;
    private final UserRepository userRepository;

    @Autowired
    RefreshTokenRotateLogoutRaceIntegrationTest(
            DataSource dataSource,
            EntityManager entityManager,
            RefreshTokenService refreshTokenService,
            PlatformTransactionManager transactionManager,
            UserRepository userRepository
    ) {
        super(dataSource);
        this.entityManager = entityManager;
        this.refreshTokenService = refreshTokenService;
        this.transactionManager = transactionManager;
        this.userRepository = userRepository;
    }

    @Test
    void concurrentRotateAndLogoutLeaveNoUsableReplacementInTheFamily() throws Exception {
        User user = saveUser();
        String rawToken = refreshTokenService.issueForUsername(user.getUsername()).value();
        CountDownLatch rotationHasUserLock = new CountDownLatch(1);
        CountDownLatch releaseUserLock = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        Future<?> rotationUserLock = executor.submit(() -> transaction.executeWithoutResult(status -> {
            entityManager.find(User.class, user.getId(), LockModeType.PESSIMISTIC_WRITE);
            rotationHasUserLock.countDown();
            await(releaseUserLock);
        }));

        try {
            assertThat(rotationHasUserLock.await(5, TimeUnit.SECONDS)).isTrue();
            Future<String> rotation = executor.submit(() -> {
                try {
                    return refreshTokenService.rotate(rawToken).value();
                } catch (AuthenticationException exception) {
                    return null;
                }
            });
            Future<?> logout = executor.submit(() -> refreshTokenService.revoke(rawToken));

            assertThatThrownBy(() -> rotation.get(300, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);
            assertThatThrownBy(() -> logout.get(300, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);

            releaseUserLock.countDown();
            String replacement = rotation.get(5, TimeUnit.SECONDS);
            logout.get(5, TimeUnit.SECONDS);

            assertThatThrownBy(() -> refreshTokenService.rotate(rawToken))
                    .isInstanceOf(AuthenticationException.class);
            if (replacement != null) {
                assertThatThrownBy(() -> refreshTokenService.rotate(replacement))
                        .isInstanceOf(AuthenticationException.class);
            }
        } finally {
            releaseUserLock.countDown();
            rotationUserLock.get(5, TimeUnit.SECONDS);
            executor.shutdownNow();
        }
    }

    private User saveUser() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String username = "logout" + suffix.substring(0, 12);
        String email = username + "@example.com";
        return userRepository.saveAndFlush(User.builder()
                .username(username)
                .email(email)
                .emailNormalized(email)
                .password("encoded-password")
                .fullName("Refresh Logout Race User")
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
