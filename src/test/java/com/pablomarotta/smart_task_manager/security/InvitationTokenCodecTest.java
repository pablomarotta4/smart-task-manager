package com.pablomarotta.smart_task_manager.security;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InvitationTokenCodecTest {

    private static final String SECRET = "invitation-token-secret-that-is-at-least-thirty-two-bytes";
    private static final String ISSUER = "smart-task-manager-invitations";
    private static final String AUDIENCE = "smart-task-manager-project-invitations";
    private static final Instant NOW = Instant.parse("2026-08-12T12:00:00Z");
    private static final int TOKEN_VERSION = 1;

    private final InvitationTokenCodec codec = new InvitationTokenCodec(
            SECRET,
            ISSUER,
            AUDIENCE,
            Clock.fixed(NOW, ZoneOffset.UTC)
    );

    @Test
    void deterministicallyEncodesAndStrictlyDecodesAnInvitationToken() {
        UUID invitationId = UUID.randomUUID();
        Instant expiresAt = NOW.plusSeconds(600);

        String token = codec.encode(invitationId, TOKEN_VERSION, NOW, expiresAt);

        InvitationTokenCodec.DecodedInvitationToken decoded = codec.decode(token);

        assertThat(codec.encode(invitationId, TOKEN_VERSION, NOW, expiresAt)).isEqualTo(token);
        assertThat(decoded.invitationId()).isEqualTo(invitationId);
        assertThat(decoded.tokenVersion()).isEqualTo(TOKEN_VERSION);
        assertThat(decoded.issuedAt()).isEqualTo(NOW);
        assertThat(decoded.expiresAt()).isEqualTo(expiresAt);
        assertThat(codec.hash(token)).hasSize(64).doesNotContain(token);
    }

    @Test
    void rejectsTokensWithTheWrongPurposeOrHeaderType() {
        UUID invitationId = UUID.randomUUID();
        String wrongPurpose = Jwts.builder()
                .header().type("project-invitation").and()
                .issuer(ISSUER)
                .audience().add(AUDIENCE).and()
                .claim("iid", invitationId.toString())
                .claim("purpose", "ACCOUNT_ACTION")
                .claim("ver", TOKEN_VERSION)
                .id(invitationId.toString())
                .issuedAt(Date.from(NOW))
                .expiration(Date.from(NOW.plusSeconds(600)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                .compact();
        String wrongType = Jwts.builder()
                .header().type("JWT").and()
                .issuer(ISSUER)
                .audience().add(AUDIENCE).and()
                .claim("iid", invitationId.toString())
                .claim("purpose", "PROJECT_INVITATION")
                .claim("ver", TOKEN_VERSION)
                .id(invitationId.toString())
                .issuedAt(Date.from(NOW))
                .expiration(Date.from(NOW.plusSeconds(600)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                .compact();

        assertThatThrownBy(() -> codec.decode(wrongPurpose)).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> codec.decode(wrongType)).isInstanceOf(JwtException.class);
    }

    @Test
    void decodesAnExpiredTokenForStateTransitionButRejectsItForGeneralUse() {
        UUID invitationId = UUID.randomUUID();
        String expiredToken = codec.encode(invitationId, TOKEN_VERSION, NOW.minusSeconds(600), NOW.minusSeconds(1));

        assertThatThrownBy(() -> codec.decode(expiredToken)).isInstanceOf(JwtException.class);
        assertThat(codec.decodeForClaim(expiredToken).invitationId()).isEqualTo(invitationId);
    }
}
