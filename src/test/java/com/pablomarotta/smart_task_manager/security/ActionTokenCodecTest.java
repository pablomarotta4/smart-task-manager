package com.pablomarotta.smart_task_manager.security;

import com.pablomarotta.smart_task_manager.model.AccountActionPurpose;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ActionTokenCodecTest {

    private static final String SECRET = "action-token-secret-that-is-at-least-thirty-two-bytes";
    private static final String ISSUER = "smart-task-manager";
    private static final String AUDIENCE = "account-action";
    private static final Instant NOW = Instant.parse("2026-08-12T12:00:00Z");

    private final ActionTokenCodec codec = new ActionTokenCodec(
            SECRET,
            ISSUER,
            AUDIENCE,
            Clock.fixed(NOW, ZoneOffset.UTC)
    );

    @Test
    void decodesSignedTokenForExpectedPurpose() {
        UUID actionId = UUID.randomUUID();
        String token = codec.encode(
                actionId,
                AccountActionPurpose.VERIFY_EMAIL,
                1,
                NOW,
                NOW.plusSeconds(300)
        );

        ActionTokenCodec.DecodedActionToken decoded = codec.decode(token, AccountActionPurpose.VERIFY_EMAIL);

        assertThat(decoded.actionId()).isEqualTo(actionId);
        assertThat(decoded.purpose()).isEqualTo(AccountActionPurpose.VERIFY_EMAIL);
        assertThat(decoded.tokenVersion()).isEqualTo(1);
        assertThat(decoded.issuedAt()).isEqualTo(NOW);
        assertThat(decoded.expiresAt()).isEqualTo(NOW.plusSeconds(300));
        assertThat(decoded.tokenId()).isNotBlank();
    }

    @Test
    void deterministicallyRegeneratesTheSameCompactTokenFromPersistedActionFields() {
        UUID actionId = UUID.randomUUID();

        String first = codec.encode(actionId, AccountActionPurpose.RESET_PASSWORD, 1, NOW, NOW.plusSeconds(300));
        String regenerated = codec.encode(actionId, AccountActionPurpose.RESET_PASSWORD, 1, NOW, NOW.plusSeconds(300));

        assertThat(regenerated).isEqualTo(first);
        assertThat(codec.decode(first, AccountActionPurpose.RESET_PASSWORD).tokenId()).isEqualTo(actionId.toString());
    }

    @Test
    void rejectsTamperedTokenAndWrongPurpose() {
        String token = codec.encode(
                UUID.randomUUID(),
                AccountActionPurpose.VERIFY_EMAIL,
                1,
                NOW,
                NOW.plusSeconds(300)
        );

        assertThatThrownBy(() -> codec.decode(token + "x", AccountActionPurpose.VERIFY_EMAIL))
                .isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> codec.decode(token, AccountActionPurpose.RESET_PASSWORD))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsExpiredTokenAndUnsupportedTokenVersion() {
        ActionTokenCodec expiredCodec = new ActionTokenCodec(
                SECRET,
                ISSUER,
                AUDIENCE,
                Clock.fixed(NOW.plusSeconds(301), ZoneOffset.UTC)
        );
        String expired = codec.encode(
                UUID.randomUUID(),
                AccountActionPurpose.RESET_PASSWORD,
                1,
                NOW,
                NOW.plusSeconds(300)
        );
        String unsupportedVersion = Jwts.builder()
                .issuer(ISSUER)
                .audience().add(AUDIENCE).and()
                .claim("aid", UUID.randomUUID().toString())
                .claim("purpose", AccountActionPurpose.RESET_PASSWORD.name())
                .claim("ver", 2)
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(NOW))
                .expiration(Date.from(NOW.plusSeconds(300)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                .compact();

        assertThatThrownBy(() -> expiredCodec.decode(expired, AccountActionPurpose.RESET_PASSWORD))
                .isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> codec.decode(unsupportedVersion, AccountActionPurpose.RESET_PASSWORD))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsAValidHs384TokenEvenWhenEveryClaimMatches() {
        UUID actionId = UUID.randomUUID();
        String hs384 = Jwts.builder()
                .header().type("account-action").and()
                .issuer(ISSUER)
                .audience().add(AUDIENCE).and()
                .claim("aid", actionId.toString())
                .claim("purpose", AccountActionPurpose.VERIFY_EMAIL.name())
                .claim("ver", 1)
                .id(actionId.toString())
                .issuedAt(Date.from(NOW))
                .expiration(Date.from(NOW.plusSeconds(300)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS384)
                .compact();

        assertThatThrownBy(() -> codec.decode(hs384, AccountActionPurpose.VERIFY_EMAIL))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsTokenWithUnexpectedTypeHeader() {
        UUID actionId = UUID.randomUUID();
        String wrongType = Jwts.builder()
                .header().type("JWT").and()
                .issuer(ISSUER)
                .audience().add(AUDIENCE).and()
                .claim("aid", actionId.toString())
                .claim("purpose", AccountActionPurpose.VERIFY_EMAIL.name())
                .claim("ver", 1)
                .id(actionId.toString())
                .issuedAt(Date.from(NOW))
                .expiration(Date.from(NOW.plusSeconds(300)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                .compact();

        assertThatThrownBy(() -> codec.decode(wrongType, AccountActionPurpose.VERIFY_EMAIL))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsMalformedActionIdClaimsWithoutLeakingTokenContents() {
        String malformedActionId = Jwts.builder()
                .header().type("account-action").and()
                .issuer(ISSUER)
                .audience().add(AUDIENCE).and()
                .claim("aid", "not-a-uuid")
                .claim("purpose", AccountActionPurpose.VERIFY_EMAIL.name())
                .claim("ver", 1)
                .id("not-a-uuid")
                .issuedAt(Date.from(NOW))
                .expiration(Date.from(NOW.plusSeconds(300)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                .compact();

        assertThatThrownBy(() -> codec.decode(malformedActionId, AccountActionPurpose.VERIFY_EMAIL))
                .isInstanceOf(JwtException.class)
                .hasMessage("Invalid account action token");
    }

    @Test
    void usesDeterministicSha256HashWithoutPersistingRawToken() {
        String token = codec.encode(
                UUID.randomUUID(),
                AccountActionPurpose.VERIFY_EMAIL,
                1,
                NOW,
                NOW.plusSeconds(300)
        );

        assertThat(codec.hash(token)).hasSize(64).isEqualTo(codec.hash(token));
        assertThat(codec.hash(token)).doesNotContain(token);
    }
}
