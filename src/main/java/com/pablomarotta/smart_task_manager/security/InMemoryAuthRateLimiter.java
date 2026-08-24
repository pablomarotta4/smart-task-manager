package com.pablomarotta.smart_task_manager.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public final class InMemoryAuthRateLimiter implements AuthRateLimiter {

    private static final String UNKNOWN_REMOTE_ADDRESS = "<unknown>";

    private final Clock clock;
    private final int maxAttempts;
    private final Duration window;
    private final int maxEntries;
    private final LinkedHashMap<BucketKey, Bucket> buckets = new LinkedHashMap<>();

    @Autowired
    public InMemoryAuthRateLimiter(
            Clock clock,
            @Value("${auth-rate-limit.max-attempts}") int maxAttempts,
            @Value("${auth-rate-limit.window-seconds}") long windowSeconds,
            @Value("${auth-rate-limit.max-entries}") int maxEntries
    ) {
        this(clock, maxAttempts, Duration.ofSeconds(windowSeconds), maxEntries);
    }

    InMemoryAuthRateLimiter(Clock clock, int maxAttempts, Duration window, int maxEntries) {
        if (clock == null) {
            throw new IllegalArgumentException("Clock is required");
        }
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("Auth rate-limit max attempts must be positive");
        }
        if (window == null || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("Auth rate-limit window must be positive");
        }
        if (maxEntries < 1) {
            throw new IllegalArgumentException("Auth rate-limit max entries must be positive");
        }
        this.clock = clock;
        this.maxAttempts = maxAttempts;
        this.window = window;
        this.maxEntries = maxEntries;
    }

    @Override
    public synchronized void check(AuthRateLimitScope scope, String remoteAddress, Collection<String> identities) {
        if (scope == null) {
            throw new IllegalArgumentException("Auth rate-limit scope is required");
        }
        Instant now = clock.instant();
        removeExpiredBuckets(now);
        List<BucketKey> keys = keysFor(scope, remoteAddress, identities);
        long retryAfterSeconds = retryAfterSeconds(now, keys);
        if (retryAfterSeconds > 0) {
            throw new AuthRateLimitExceededException(retryAfterSeconds);
        }
        if (wouldExceedCapacity(keys)) {
            throw new AuthRateLimitExceededException(earliestActiveRetryAfterSeconds(now));
        }
        for (BucketKey key : keys) {
            Bucket bucket = buckets.get(key);
            if (bucket == null) {
                bucket = new Bucket(now);
                buckets.put(key, bucket);
            }
            bucket.attempts++;
        }
    }

    synchronized Set<String> snapshotKeys() {
        return buckets.keySet().stream()
                .map(BucketKey::safeSnapshotValue)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private List<BucketKey> keysFor(
            AuthRateLimitScope scope,
            String remoteAddress,
            Collection<String> identities
    ) {
        List<BucketKey> keys = new ArrayList<>();
        keys.add(new BucketKey(scope, BucketKind.IP, normalizedRemoteAddress(remoteAddress)));
        LinkedHashSet<String> identityHashes = new LinkedHashSet<>();
        if (identities != null) {
            for (String identity : identities) {
                normalizedIdentityHash(identity).ifPresent(identityHashes::add);
            }
        }
        for (String identityHash : identityHashes) {
            keys.add(new BucketKey(scope, BucketKind.IDENTITY, identityHash));
        }
        return keys;
    }

    private String normalizedRemoteAddress(String remoteAddress) {
        if (remoteAddress == null || remoteAddress.isBlank()) {
            return UNKNOWN_REMOTE_ADDRESS;
        }
        return remoteAddress.strip();
    }

    private java.util.Optional<String> normalizedIdentityHash(String identity) {
        if (identity == null) {
            return java.util.Optional.empty();
        }
        String normalized = identity.strip().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(sha256(normalized));
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void removeExpiredBuckets(Instant now) {
        buckets.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().windowStartedAt.plus(window)));
    }

    private long retryAfterSeconds(Instant now, List<BucketKey> keys) {
        long retryAfterSeconds = 0;
        for (BucketKey key : keys) {
            Bucket bucket = buckets.get(key);
            if (bucket != null && bucket.attempts >= maxAttempts) {
                Duration remaining = Duration.between(now, bucket.windowStartedAt.plus(window));
                retryAfterSeconds = Math.max(retryAfterSeconds, ceilPositiveSeconds(remaining));
            }
        }
        return retryAfterSeconds;
    }

    private long ceilPositiveSeconds(Duration duration) {
        long seconds = duration.toSeconds();
        if (duration.minusSeconds(seconds).isZero()) {
            return Math.max(1, seconds);
        }
        return Math.max(1, seconds + 1);
    }

    private boolean wouldExceedCapacity(List<BucketKey> keys) {
        long missingBuckets = keys.stream()
                .filter(key -> !buckets.containsKey(key))
                .count();
        return buckets.size() + missingBuckets > maxEntries;
    }

    private long earliestActiveRetryAfterSeconds(Instant now) {
        return buckets.values().stream()
                .map(bucket -> Duration.between(now, bucket.windowStartedAt.plus(window)))
                .mapToLong(this::ceilPositiveSeconds)
                .min()
                .orElseGet(() -> ceilPositiveSeconds(window));
    }

    private enum BucketKind {
        IP,
        IDENTITY
    }

    private record BucketKey(AuthRateLimitScope scope, BucketKind kind, String value) {
        private String safeSnapshotValue() {
            return scope.name() + ':' + kind.name() + ':' + value;
        }
    }

    private static final class Bucket {
        private final Instant windowStartedAt;
        private int attempts;

        private Bucket(Instant windowStartedAt) {
            this.windowStartedAt = windowStartedAt;
        }
    }
}
