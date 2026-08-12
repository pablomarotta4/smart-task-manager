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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final String ISSUER = "smart-task-manager-access";
    private static final String AUDIENCE = "smart-task-manager-api";
    private static final Instant NOW = Instant.parse("2026-08-12T12:00:00Z");

    private final JwtTokenProvider provider = new JwtTokenProvider(
            SECRET,
            3_600_000,
            ISSUER,
            AUDIENCE,
            Clock.fixed(NOW, ZoneOffset.UTC)
    );

    @Test
    void issuesHs256AccessTokensBoundToTheImmutableAccountIdentity() {
        String token = provider.generateToken("testuser", 42L, 3);

        var claims = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .clock(() -> Date.from(NOW))
                .build()
                .parseSignedClaims(token);
        JwtTokenProvider.AccessTokenClaims parsed = provider.parseAccessToken(token);

        assertThat(claims.getHeader().getAlgorithm()).isEqualTo("HS256");
        assertThat(claims.getPayload().getSubject()).isEqualTo("testuser");
        assertThat(claims.getPayload().getIssuer()).isEqualTo(ISSUER);
        assertThat(claims.getPayload().getAudience()).contains(AUDIENCE);
        assertThat(claims.getPayload().getIssuedAt().toInstant()).isEqualTo(NOW);
        assertThat(claims.getPayload().getExpiration().toInstant()).isEqualTo(NOW.plusSeconds(3_600));
        assertThat(parsed.username()).isEqualTo("testuser");
        assertThat(parsed.userId()).isEqualTo(42L);
        assertThat(parsed.authVersion()).isEqualTo(3);
    }

    @Test
    void rejectsTokensMissingRequiredAccessIdentityMetadata() {
        String missingVersion = Jwts.builder()
                .header().type("access").and()
                .issuer(ISSUER)
                .audience().add(AUDIENCE).and()
                .subject("testuser")
                .claim("uid", 42L)
                .issuedAt(Date.from(NOW))
                .expiration(Date.from(NOW.plusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                .compact();

        assertThat(provider.validateToken(missingVersion)).isFalse();
        assertThatThrownBy(() -> provider.parseAccessToken(missingVersion))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsTokensWithWrongIssuerAudienceOrAlgorithm() {
        String wrongIssuer = signedAccessToken("other-issuer", AUDIENCE, Jwts.SIG.HS256);
        String wrongAudience = signedAccessToken(ISSUER, "other-audience", Jwts.SIG.HS256);
        String hs384 = signedAccessToken(ISSUER, AUDIENCE, Jwts.SIG.HS384);

        assertThat(provider.validateToken(wrongIssuer)).isFalse();
        assertThat(provider.validateToken(wrongAudience)).isFalse();
        assertThat(provider.validateToken(hs384)).isFalse();
    }

    @Test
    void rejectsExpiredOrFutureIssuedAccessTokens() {
        String expired = accessToken(NOW.minusSeconds(120), NOW.minusSeconds(60));
        String futureIssued = accessToken(NOW.plusSeconds(60), NOW.plusSeconds(120));

        assertThat(provider.validateToken(expired)).isFalse();
        assertThat(provider.validateToken(futureIssued)).isFalse();
    }

    private String signedAccessToken(String issuer, String audience, io.jsonwebtoken.security.MacAlgorithm algorithm) {
        return Jwts.builder()
                .header().type("access").and()
                .issuer(issuer)
                .audience().add(audience).and()
                .subject("testuser")
                .claim("uid", 42L)
                .claim("av", 3)
                .issuedAt(Date.from(NOW))
                .expiration(Date.from(NOW.plusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), algorithm)
                .compact();
    }

    private String accessToken(Instant issuedAt, Instant expiresAt) {
        return Jwts.builder()
                .header().type("access").and()
                .issuer(ISSUER)
                .audience().add(AUDIENCE).and()
                .subject("testuser")
                .claim("uid", 42L)
                .claim("av", 3)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                .compact();
    }
}
