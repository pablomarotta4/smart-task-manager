package com.pablomarotta.smart_task_manager.service;

import com.pablomarotta.smart_task_manager.model.RefreshToken;
import com.pablomarotta.smart_task_manager.model.User;
import com.pablomarotta.smart_task_manager.repository.RefreshTokenRepository;
import com.pablomarotta.smart_task_manager.repository.UserRepository;
import com.pablomarotta.smart_task_manager.security.RefreshTokenCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.AuthenticationException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-11T15:00:00Z");
    private static final long REFRESH_EXPIRATION_MS = 604_800_000L;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenCodec refreshTokenCodec;

    private RefreshTokenService refreshTokenService;
    private User activeUser;

    @BeforeEach
    void setUp() {
        refreshTokenService = new RefreshTokenService(
                refreshTokenRepository,
                userRepository,
                refreshTokenCodec,
                Clock.fixed(NOW, ZoneOffset.UTC),
                REFRESH_EXPIRATION_MS
        );
        activeUser = User.builder()
                .id(7L)
                .username("pablo")
                .email("pablo@example.com")
                .password("encoded")
                .fullName("Pablo Marotta")
                .active(true)
                .build();
    }

    @Test
    void issueForUsernameStoresOnlyTheHashAndReturnsTheOpaqueToken() {
        when(userRepository.findByUsername("pablo")).thenReturn(Optional.of(activeUser));
        when(refreshTokenCodec.generate()).thenReturn("raw-refresh-token");
        when(refreshTokenCodec.hash("raw-refresh-token")).thenReturn("stored-digest");

        RefreshTokenService.IssuedRefreshToken issued = refreshTokenService.issueForUsername("pablo");

        ArgumentCaptor<RefreshToken> savedToken = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(savedToken.capture());
        assertThat(savedToken.getValue().getTokenHash()).isEqualTo("stored-digest");
        assertThat(savedToken.getValue().getTokenHash()).doesNotContain("raw-refresh-token");
        assertThat(savedToken.getValue().getIssuedAt()).isEqualTo(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        assertThat(savedToken.getValue().getExpiresAt())
                .isEqualTo(LocalDateTime.ofInstant(NOW.plusMillis(REFRESH_EXPIRATION_MS), ZoneOffset.UTC));
        assertThat(issued.value()).isEqualTo("raw-refresh-token");
        assertThat(issued.username()).isEqualTo("pablo");
    }

    @Test
    void rotateRevokesTheClaimedTokenAndIssuesAReplacementForTheSameUser() {
        RefreshToken current = validStoredToken();
        when(refreshTokenCodec.hash("current-token")).thenReturn("current-digest");
        when(refreshTokenRepository.findForUpdateByTokenHash("current-digest"))
                .thenReturn(Optional.of(current));
        when(refreshTokenCodec.generate()).thenReturn("replacement-token");
        when(refreshTokenCodec.hash("replacement-token")).thenReturn("replacement-digest");

        RefreshTokenService.IssuedRefreshToken replacement = refreshTokenService.rotate("current-token");

        assertThat(current.getRevokedAt()).isEqualTo(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        ArgumentCaptor<RefreshToken> savedTokens = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository, org.mockito.Mockito.times(2)).save(savedTokens.capture());
        assertThat(savedTokens.getAllValues().get(1).getTokenHash()).isEqualTo("replacement-digest");
        assertThat(replacement.value()).isEqualTo("replacement-token");
        assertThat(replacement.username()).isEqualTo("pablo");
    }

    @Test
    void rotateRejectsAReplayedRevokedToken() {
        RefreshToken revoked = validStoredToken();
        revoked.setRevokedAt(LocalDateTime.ofInstant(NOW.minusSeconds(10), ZoneOffset.UTC));
        when(refreshTokenCodec.hash("replayed-token")).thenReturn("replayed-digest");
        when(refreshTokenRepository.findForUpdateByTokenHash("replayed-digest"))
                .thenReturn(Optional.of(revoked));

        assertThatThrownBy(() -> refreshTokenService.rotate("replayed-token"))
                .isInstanceOf(AuthenticationException.class)
                .hasMessage("Refresh token is invalid or expired");

        verify(refreshTokenCodec, never()).generate();
    }

    @Test
    void rotateRejectsAnExpiredToken() {
        RefreshToken expired = validStoredToken();
        expired.setExpiresAt(LocalDateTime.ofInstant(NOW.minusSeconds(1), ZoneOffset.UTC));
        when(refreshTokenCodec.hash("expired-token")).thenReturn("expired-digest");
        when(refreshTokenRepository.findForUpdateByTokenHash("expired-digest"))
                .thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> refreshTokenService.rotate("expired-token"))
                .isInstanceOf(AuthenticationException.class)
                .hasMessage("Refresh token is invalid or expired");
    }

    @Test
    void rotateRejectsADeactivatedUser() {
        RefreshToken token = validStoredToken();
        token.getUser().setActive(false);
        when(refreshTokenCodec.hash("inactive-token")).thenReturn("inactive-digest");
        when(refreshTokenRepository.findForUpdateByTokenHash("inactive-digest"))
                .thenReturn(Optional.of(token));

        assertThatThrownBy(() -> refreshTokenService.rotate("inactive-token"))
                .isInstanceOf(AuthenticationException.class)
                .hasMessage("Refresh token is invalid or expired");
    }

    @Test
    void revokeIsIdempotentForUnknownTokens() {
        when(refreshTokenCodec.hash("unknown-token")).thenReturn("unknown-digest");
        when(refreshTokenRepository.findForUpdateByTokenHash("unknown-digest"))
                .thenReturn(Optional.empty());

        refreshTokenService.revoke("unknown-token");

        verify(refreshTokenRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private RefreshToken validStoredToken() {
        LocalDateTime now = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
        return RefreshToken.builder()
                .id(11L)
                .user(activeUser)
                .tokenHash("current-digest")
                .issuedAt(now.minusDays(1))
                .expiresAt(now.plusDays(6))
                .build();
    }
}
