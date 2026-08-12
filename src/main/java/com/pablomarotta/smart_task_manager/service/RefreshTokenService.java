package com.pablomarotta.smart_task_manager.service;

import com.pablomarotta.smart_task_manager.model.RefreshToken;
import com.pablomarotta.smart_task_manager.model.User;
import com.pablomarotta.smart_task_manager.repository.RefreshTokenRepository;
import com.pablomarotta.smart_task_manager.repository.UserRepository;
import com.pablomarotta.smart_task_manager.security.AuthenticatedUserPrincipal;
import com.pablomarotta.smart_task_manager.security.RefreshTokenCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
public class RefreshTokenService {

    private static final String INVALID_TOKEN_MESSAGE = "Refresh token is invalid or expired";

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final RefreshTokenCodec refreshTokenCodec;
    private final Clock clock;
    private final long refreshExpirationMs;

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            UserRepository userRepository,
            RefreshTokenCodec refreshTokenCodec,
            Clock clock,
            @Value("${jwt.refresh-expiration}") long refreshExpirationMs
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
        this.refreshTokenCodec = refreshTokenCodec;
        this.clock = clock;
        this.refreshExpirationMs = refreshExpirationMs;
    }

    @Transactional
    public IssuedRefreshToken issueForUsername(String username) {
        User user = userRepository.findActiveForUpdateByUsername(username)
                .orElseThrow(this::invalidToken);
        return issueForUser(user, UUID.randomUUID());
    }

    @Transactional
    public IssuedRefreshToken issueForPrincipal(AuthenticatedUserPrincipal principal) {
        if (principal == null || principal.getUserId() == null) {
            throw invalidToken();
        }
        User user = userRepository.findActiveForUpdateById(principal.getUserId())
                .orElseThrow(this::invalidToken);
        if (!user.getUsername().equals(principal.getUsername())
                || authVersionOf(user) != principal.getAuthVersion()) {
            throw invalidToken();
        }
        return issueForUser(user, UUID.randomUUID());
    }

    @Transactional(noRollbackFor = BadCredentialsException.class)
    public IssuedRefreshToken rotate(String rawToken) {
        requireToken(rawToken);
        String tokenHash = refreshTokenCodec.hash(rawToken);
        Long userId = refreshTokenRepository.findUserIdByTokenHash(tokenHash)
                .orElseThrow(this::invalidToken);
        User user = userRepository.findActiveForUpdateById(userId)
                .orElseThrow(this::invalidToken);
        RefreshToken current = refreshTokenRepository
                .findForUpdateByTokenHash(tokenHash)
                .orElseThrow(this::invalidToken);
        LocalDateTime now = now();
        if (current.getRevokedAt() != null) {
            revokeFamily(userId, current.getFamilyId(), now);
            throw invalidToken();
        }
        if (!current.getExpiresAt().isAfter(now)) {
            throw invalidToken();
        }

        current.setRevokedAt(now);
        refreshTokenRepository.save(current);
        return issueForUser(user, current.getFamilyId());
    }

    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        String tokenHash = refreshTokenCodec.hash(rawToken);
        refreshTokenRepository.findUserIdByTokenHash(tokenHash)
                .flatMap(userRepository::findActiveForUpdateById)
                .flatMap(user -> refreshTokenRepository.findForUpdateByTokenHash(tokenHash)
                        .map(token -> new FamilyToken(user.getId(), token.getFamilyId())))
                .ifPresent(familyToken -> revokeFamily(
                        familyToken.userId(), familyToken.familyId(), now()
                ));
    }

    private IssuedRefreshToken issueForUser(User user, UUID familyId) {
        LocalDateTime issuedAt = now();
        LocalDateTime expiresAt = LocalDateTime.ofInstant(
                Instant.now(clock).plusMillis(refreshExpirationMs),
                ZoneOffset.UTC
        );
        String rawToken = refreshTokenCodec.generate();
        refreshTokenRepository.save(RefreshToken.builder()
                .user(user)
                .tokenHash(refreshTokenCodec.hash(rawToken))
                .familyId(familyId)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build());
        return new IssuedRefreshToken(
                rawToken,
                user.getUsername(),
                user.getId(),
                authVersionOf(user),
                expiresAt
        );
    }

    private void requireToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw invalidToken();
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(Instant.now(clock), ZoneOffset.UTC);
    }

    private BadCredentialsException invalidToken() {
        return new BadCredentialsException(INVALID_TOKEN_MESSAGE);
    }

    private void revokeFamily(Long userId, UUID familyId, LocalDateTime revokedAt) {
        if (familyId != null) {
            refreshTokenRepository.revokeFamilyByUserIdAndFamilyId(userId, familyId, revokedAt);
        }
    }

    @Transactional
    public void revokeAllForUserId(Long userId) {
        if (userId != null) {
            refreshTokenRepository.deleteAllByUserId(userId);
        }
    }

    private int authVersionOf(User user) {
        return user.getAuthVersion() == null ? 0 : user.getAuthVersion();
    }

    private record FamilyToken(Long userId, UUID familyId) {
    }

    public record IssuedRefreshToken(
            String value,
            String username,
            Long userId,
            int authVersion,
            LocalDateTime expiresAt
    ) {
    }
}
