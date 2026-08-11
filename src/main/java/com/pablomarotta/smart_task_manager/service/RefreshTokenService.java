package com.pablomarotta.smart_task_manager.service;

import com.pablomarotta.smart_task_manager.model.RefreshToken;
import com.pablomarotta.smart_task_manager.model.User;
import com.pablomarotta.smart_task_manager.repository.RefreshTokenRepository;
import com.pablomarotta.smart_task_manager.repository.UserRepository;
import com.pablomarotta.smart_task_manager.security.RefreshTokenCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

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
        User user = userRepository.findByUsername(username)
                .filter(candidate -> Boolean.TRUE.equals(candidate.getActive()))
                .orElseThrow(this::invalidToken);
        return issueForUser(user);
    }

    @Transactional
    public IssuedRefreshToken rotate(String rawToken) {
        requireToken(rawToken);
        RefreshToken current = refreshTokenRepository
                .findForUpdateByTokenHash(refreshTokenCodec.hash(rawToken))
                .orElseThrow(this::invalidToken);
        LocalDateTime now = now();
        if (current.getRevokedAt() != null
                || !current.getExpiresAt().isAfter(now)
                || !Boolean.TRUE.equals(current.getUser().getActive())) {
            throw invalidToken();
        }

        current.setRevokedAt(now);
        refreshTokenRepository.save(current);
        return issueForUser(current.getUser());
    }

    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findForUpdateByTokenHash(refreshTokenCodec.hash(rawToken))
                .filter(token -> token.getRevokedAt() == null)
                .ifPresent(token -> {
                    token.setRevokedAt(now());
                    refreshTokenRepository.save(token);
                });
    }

    private IssuedRefreshToken issueForUser(User user) {
        LocalDateTime issuedAt = now();
        LocalDateTime expiresAt = LocalDateTime.ofInstant(
                Instant.now(clock).plusMillis(refreshExpirationMs),
                ZoneOffset.UTC
        );
        String rawToken = refreshTokenCodec.generate();
        refreshTokenRepository.save(RefreshToken.builder()
                .user(user)
                .tokenHash(refreshTokenCodec.hash(rawToken))
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build());
        return new IssuedRefreshToken(rawToken, user.getUsername(), expiresAt);
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

    public record IssuedRefreshToken(String value, String username, LocalDateTime expiresAt) {
    }
}

