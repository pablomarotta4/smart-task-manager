package com.pablomarotta.smart_task_manager.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryAuthRateLimiterTest {

    private static final String IP = "203.0.113.17";
    private final MutableClock clock = new MutableClock(Instant.parse("2026-08-12T18:00:00Z"));

    @Test
    void allowsRequestsUntilTheConfiguredLimitThenRejectsTheNextIpAttempt() {
        InMemoryAuthRateLimiter limiter = limiter(2, Duration.ofSeconds(60), 20);

        limiter.check(AuthRateLimitScope.LOGIN, IP, List.of("alice"));
        limiter.check(AuthRateLimitScope.LOGIN, IP, List.of("bob"));

        assertThatThrownBy(() -> limiter.check(AuthRateLimitScope.LOGIN, IP, List.of("charlie")))
                .isInstanceOf(AuthRateLimitExceededException.class)
                .extracting("retryAfterSeconds")
                .isEqualTo(60L);
    }

    @Test
    void rejectsAnIdentityAcrossDifferentRemoteAddresses() {
        InMemoryAuthRateLimiter limiter = limiter(2, Duration.ofSeconds(60), 20);

        limiter.check(AuthRateLimitScope.LOGIN, "198.51.100.1", List.of("  Alice@Example.com "));
        limiter.check(AuthRateLimitScope.LOGIN, "198.51.100.2", List.of("alice@example.com"));

        assertThatThrownBy(() -> limiter.check(AuthRateLimitScope.LOGIN, "198.51.100.3", List.of("ALICE@example.COM")))
                .isInstanceOf(AuthRateLimitExceededException.class);
    }

    @Test
    void isolatesEachAuthScope() {
        InMemoryAuthRateLimiter limiter = limiter(1, Duration.ofSeconds(60), 20);

        limiter.check(AuthRateLimitScope.LOGIN, IP, List.of("alice"));
        limiter.check(AuthRateLimitScope.REGISTER, IP, List.of("alice"));

        assertThatThrownBy(() -> limiter.check(AuthRateLimitScope.LOGIN, IP, List.of("alice")))
                .isInstanceOf(AuthRateLimitExceededException.class);
    }

    @Test
    void countsTheIpOnlyOnceWhenSeveralIdentitiesAreProvided() {
        InMemoryAuthRateLimiter limiter = limiter(2, Duration.ofSeconds(60), 20);

        limiter.check(AuthRateLimitScope.PASSWORD_RESET_REQUEST, IP, List.of("alice@example.com", "bob@example.com"));
        limiter.check(AuthRateLimitScope.PASSWORD_RESET_REQUEST, IP, List.of("carol@example.com"));

        assertThatThrownBy(() -> limiter.check(AuthRateLimitScope.PASSWORD_RESET_REQUEST, IP, List.of("dave@example.com")))
                .isInstanceOf(AuthRateLimitExceededException.class);
    }

    @Test
    void permitsARequestAfterTheWindowExpires() {
        InMemoryAuthRateLimiter limiter = limiter(1, Duration.ofSeconds(60), 20);
        limiter.check(AuthRateLimitScope.EMAIL_VERIFICATION_RESEND, IP, List.of("alice"));

        clock.advance(Duration.ofSeconds(60));

        limiter.check(AuthRateLimitScope.EMAIL_VERIFICATION_RESEND, IP, List.of("alice"));
    }

    @Test
    void reportsAtLeastOneSecondUntilTheCurrentWindowPermitsAnotherRequest() {
        InMemoryAuthRateLimiter limiter = limiter(1, Duration.ofSeconds(60), 20);
        limiter.check(AuthRateLimitScope.PASSWORD_RESET_CONFIRM, IP, List.of("action-token"));
        clock.advance(Duration.ofMillis(59_001));

        assertThatThrownBy(() -> limiter.check(AuthRateLimitScope.PASSWORD_RESET_CONFIRM, IP, List.of("action-token")))
                .isInstanceOfSatisfying(AuthRateLimitExceededException.class, exception ->
                        assertThat(exception.getRetryAfterSeconds()).isEqualTo(1)
                );
    }

    @Test
    void rejectsNewBucketsWhenTheConfiguredMemoryBoundIsReached() {
        InMemoryAuthRateLimiter limiter = limiter(5, Duration.ofSeconds(60), 2);

        limiter.check(AuthRateLimitScope.LOGIN, "198.51.100.1", List.of());
        limiter.check(AuthRateLimitScope.LOGIN, "198.51.100.2", List.of());

        assertThatThrownBy(() -> limiter.check(AuthRateLimitScope.LOGIN, "198.51.100.3", List.of()))
                .isInstanceOfSatisfying(AuthRateLimitExceededException.class, exception ->
                        assertThat(exception.getRetryAfterSeconds()).isEqualTo(60)
                );

        assertThat(limiter.snapshotKeys()).hasSize(2);
        assertThat(limiter.snapshotKeys()).anyMatch(key -> key.contains("198.51.100.1"));
        assertThat(limiter.snapshotKeys()).anyMatch(key -> key.contains("198.51.100.2"));
    }

    @Test
    void sameIpNovelIdentityFloodingCannotEvictOrResetItsActiveIpBucket() {
        InMemoryAuthRateLimiter limiter = limiter(2, Duration.ofSeconds(60), 2);

        limiter.check(AuthRateLimitScope.LOGIN, IP, List.of("alice"));

        for (String novelIdentity : List.of("bob", "charlie", "dave")) {
            assertThatThrownBy(() -> limiter.check(AuthRateLimitScope.LOGIN, IP, List.of(novelIdentity)))
                    .isInstanceOfSatisfying(AuthRateLimitExceededException.class, exception ->
                            assertThat(exception.getRetryAfterSeconds()).isPositive()
                    );
        }

        limiter.check(AuthRateLimitScope.LOGIN, IP, List.of("alice"));

        assertThatThrownBy(() -> limiter.check(AuthRateLimitScope.LOGIN, IP, List.of("alice")))
                .isInstanceOf(AuthRateLimitExceededException.class);
        assertThat(limiter.snapshotKeys()).hasSize(2);
    }

    @Test
    void distinctIpFloodingCannotEvictOrResetItsActiveIdentityBucket() {
        InMemoryAuthRateLimiter limiter = limiter(2, Duration.ofSeconds(60), 2);

        limiter.check(AuthRateLimitScope.LOGIN, IP, List.of("alice"));

        for (String novelIp : List.of("198.51.100.1", "198.51.100.2", "198.51.100.3")) {
            assertThatThrownBy(() -> limiter.check(AuthRateLimitScope.LOGIN, novelIp, List.of("alice")))
                    .isInstanceOfSatisfying(AuthRateLimitExceededException.class, exception ->
                            assertThat(exception.getRetryAfterSeconds()).isPositive()
                    );
        }

        limiter.check(AuthRateLimitScope.LOGIN, IP, List.of("alice"));

        assertThatThrownBy(() -> limiter.check(AuthRateLimitScope.LOGIN, IP, List.of("alice")))
                .isInstanceOf(AuthRateLimitExceededException.class);
        assertThat(limiter.snapshotKeys()).hasSize(2);
    }

    @Test
    void neverRetainsRawIdentityOrActionTokenInCacheKeys() {
        InMemoryAuthRateLimiter limiter = limiter(5, Duration.ofSeconds(60), 20);
        String email = "  Alice.Example@Example.com ";
        String actionToken = "raw-account-action-token-must-not-be-retained";

        limiter.check(AuthRateLimitScope.EMAIL_VERIFICATION_CONFIRM, IP, List.of(email, actionToken));

        assertThat(limiter.snapshotKeys())
                .noneMatch(key -> key.contains(email.strip()))
                .noneMatch(key -> key.contains(email.strip().toLowerCase()))
                .noneMatch(key -> key.contains(actionToken));
    }

    @Test
    void concurrentChecksNeverAllowMoreThanTheConfiguredLimit() throws Exception {
        InMemoryAuthRateLimiter limiter = new InMemoryAuthRateLimiter(
                Clock.fixed(clock.instant(), ZoneOffset.UTC), 3, Duration.ofSeconds(60), 100
        );
        ExecutorService executor = Executors.newFixedThreadPool(12);
        try {
            List<Callable<Boolean>> checks = java.util.stream.IntStream.range(0, 24)
                    .<Callable<Boolean>>mapToObj(ignored -> () -> {
                        try {
                            limiter.check(AuthRateLimitScope.LOGIN, IP, List.of("alice"));
                            return true;
                        } catch (AuthRateLimitExceededException exception) {
                            return false;
                        }
                    })
                    .toList();

            List<Future<Boolean>> results = executor.invokeAll(checks);

            long allowed = 0;
            for (Future<Boolean> result : results) {
                if (result.get(5, TimeUnit.SECONDS)) {
                    allowed++;
                }
            }
            assertThat(allowed).isEqualTo(3);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void rejectsNonPositiveLimiterConfiguration() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new InMemoryAuthRateLimiter(clock, 0, Duration.ofSeconds(60), 1));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new InMemoryAuthRateLimiter(clock, 1, Duration.ZERO, 1));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new InMemoryAuthRateLimiter(clock, 1, Duration.ofSeconds(60), 0));
    }

    private InMemoryAuthRateLimiter limiter(int maxAttempts, Duration window, int maxEntries) {
        return new InMemoryAuthRateLimiter(clock, maxAttempts, window, maxEntries);
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }
    }
}
