package com.pablomarotta.smart_task_manager.integration;

import com.pablomarotta.smart_task_manager.model.AccountActionPurpose;
import com.pablomarotta.smart_task_manager.model.AccountActionRequest;
import com.pablomarotta.smart_task_manager.model.User;
import com.pablomarotta.smart_task_manager.repository.AccountActionRequestRepository;
import com.pablomarotta.smart_task_manager.repository.RefreshTokenRepository;
import com.pablomarotta.smart_task_manager.repository.UserRepository;
import com.pablomarotta.smart_task_manager.security.ActionTokenCodec;
import com.pablomarotta.smart_task_manager.security.AuthenticatedUserPrincipal;
import com.pablomarotta.smart_task_manager.service.AccountActionService;
import com.pablomarotta.smart_task_manager.service.RefreshTokenService;
import com.pablomarotta.smart_task_manager.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;

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

@SpringBootTest
class LoginIssuanceSecurityRaceIntegrationTest extends PostgresIntegrationTest {

    private final AccountActionRequestRepository accountActionRequestRepository;
    private final AccountActionService accountActionService;
    private final ActionTokenCodec actionTokenCodec;
    private final AuthenticationManager authenticationManager;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenService refreshTokenService;
    private final UserRepository userRepository;
    private final UserService userService;

    @Autowired
    LoginIssuanceSecurityRaceIntegrationTest(
            DataSource dataSource,
            AccountActionRequestRepository accountActionRequestRepository,
            AccountActionService accountActionService,
            ActionTokenCodec actionTokenCodec,
            AuthenticationManager authenticationManager,
            PasswordEncoder passwordEncoder,
            RefreshTokenRepository refreshTokenRepository,
            RefreshTokenService refreshTokenService,
            UserRepository userRepository,
            UserService userService
    ) {
        super(dataSource);
        this.accountActionRequestRepository = accountActionRequestRepository;
        this.accountActionService = accountActionService;
        this.actionTokenCodec = actionTokenCodec;
        this.authenticationManager = authenticationManager;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenService = refreshTokenService;
        this.userRepository = userRepository;
        this.userService = userService;
    }

    @Test
    void oldPasswordAuthenticationCannotIssueTokensAfterPasswordResetCommits() throws Exception {
        User user = saveUser();
        String resetToken = issueResetToken(user);

        LoginAttempt attempt = authenticateBeforeAndIssueAfter(
                user,
                () -> accountActionService.confirmPasswordReset(resetToken, "changed-password")
        );

        assertThat(attempt.failure()).isInstanceOf(AuthenticationException.class);
        assertThat(attempt.issued()).isNull();
        assertThat(userRepository.findById(user.getId()).orElseThrow().getAuthVersion()).isEqualTo(1);
        assertThat(refreshTokenRepository.findAll()).isEmpty();
    }

    @Test
    void oldPasswordAuthenticationCannotIssueTokensAfterDeactivationCommits() throws Exception {
        User user = saveUser();

        LoginAttempt attempt = authenticateBeforeAndIssueAfter(
                user,
                () -> userService.deleteUser(user.getUsername())
        );

        assertThat(attempt.failure()).isInstanceOf(AuthenticationException.class);
        assertThat(attempt.issued()).isNull();
        assertThat(userRepository.findById(user.getId()).orElseThrow().getActive()).isFalse();
        assertThat(refreshTokenRepository.findAll()).isEmpty();
    }

    private LoginAttempt authenticateBeforeAndIssueAfter(User user, Runnable securityMutation) throws Exception {
        CountDownLatch credentialsObserved = new CountDownLatch(1);
        CountDownLatch issueAllowed = new CountDownLatch(1);
        AtomicReference<AuthenticatedUserPrincipal> observedPrincipal = new AtomicReference<>();
        AtomicReference<RefreshTokenService.IssuedRefreshToken> issued = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> login = executor.submit(() -> {
            try {
                Authentication authentication = authenticationManager.authenticate(
                        new UsernamePasswordAuthenticationToken(user.getUsername(), "original-password")
                );
                if (!(authentication.getPrincipal() instanceof AuthenticatedUserPrincipal principal)) {
                    throw new IllegalStateException("Authentication did not expose the observed user identity");
                }
                observedPrincipal.set(principal);
            } catch (Throwable throwable) {
                failure.set(throwable);
                return;
            } finally {
                credentialsObserved.countDown();
            }
            try {
                await(issueAllowed);
                issued.set(refreshTokenService.issueForPrincipal(observedPrincipal.get()));
            } catch (Throwable throwable) {
                failure.set(throwable);
            }
        });

        try {
            assertThat(credentialsObserved.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(failure.get()).isNull();
            securityMutation.run();
            issueAllowed.countDown();
            login.get(5, TimeUnit.SECONDS);
            return new LoginAttempt(issued.get(), failure.get());
        } finally {
            issueAllowed.countDown();
            executor.shutdownNow();
        }
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
        String username = "loginrace" + suffix.substring(0, 12);
        String email = username + "@example.com";
        return userRepository.saveAndFlush(User.builder()
                .username(username)
                .email(email)
                .emailNormalized(email)
                .password(passwordEncoder.encode("original-password"))
                .fullName("Login Race User")
                .build());
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for login issuance");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for login issuance", exception);
        }
    }

    private record LoginAttempt(RefreshTokenService.IssuedRefreshToken issued, Throwable failure) {
    }
}
