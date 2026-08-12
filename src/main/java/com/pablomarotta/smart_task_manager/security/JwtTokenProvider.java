package com.pablomarotta.smart_task_manager.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;

@Component
public class JwtTokenProvider {

    private static final String ACCESS_TOKEN_TYPE = "access";
    private static final String USER_ID_CLAIM = "uid";
    private static final String AUTH_VERSION_CLAIM = "av";

    private final SecretKey key;
    private final long jwtExpirationMs;
    private final String issuer;
    private final String audience;
    private final Clock clock;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String jwtSecret,
            @Value("${jwt.expiration}") long jwtExpirationMs,
            @Value("${jwt.issuer}") String issuer,
            @Value("${jwt.audience}") String audience,
            Clock clock
    ) {
        this.key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        this.jwtExpirationMs = jwtExpirationMs;
        this.issuer = issuer;
        this.audience = audience;
        this.clock = clock;
    }

    public String generateToken(String username, Long userId, int authVersion) {
        if (username == null || username.isBlank() || userId == null || userId < 1 || authVersion < 0) {
            throw new IllegalArgumentException("Access token identity is invalid");
        }
        Instant issuedAt = Instant.now(clock);
        Instant expiresAt = issuedAt.plusMillis(jwtExpirationMs);
        return Jwts.builder()
                .header().type(ACCESS_TOKEN_TYPE).and()
                .issuer(issuer)
                .audience().add(audience).and()
                .subject(username)
                .claim(USER_ID_CLAIM, userId)
                .claim(AUTH_VERSION_CLAIM, authVersion)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public AccessTokenClaims parseAccessToken(String token) {
        Jws<Claims> parsed = Jwts.parser()
                .verifyWith(key)
                .clock(() -> Date.from(Instant.now(clock)))
                .sig().add(Jwts.SIG.HS256).and()
                .build()
                .parseSignedClaims(token);
        Claims claims = parsed.getPayload();
        validateClaims(claims, parsed.getHeader().getType(), parsed.getHeader().getAlgorithm());
        return new AccessTokenClaims(
                claims.getSubject(),
                claims.get(USER_ID_CLAIM, Long.class),
                claims.get(AUTH_VERSION_CLAIM, Integer.class)
        );
    }

    public boolean validateToken(String token) {
        try {
            parseAccessToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    private void validateClaims(Claims claims, String tokenType, String algorithm) {
        Date issuedAt = claims.getIssuedAt();
        Date expiresAt = claims.getExpiration();
        Long userId = claims.get(USER_ID_CLAIM, Long.class);
        Integer authVersion = claims.get(AUTH_VERSION_CLAIM, Integer.class);
        Instant now = Instant.now(clock);
        if (!ACCESS_TOKEN_TYPE.equals(tokenType)
                || !"HS256".equals(algorithm)
                || !issuer.equals(claims.getIssuer())
                || claims.getAudience() == null
                || !claims.getAudience().contains(audience)
                || claims.getSubject() == null
                || claims.getSubject().isBlank()
                || userId == null
                || userId < 1
                || authVersion == null
                || authVersion < 0
                || issuedAt == null
                || expiresAt == null
                || issuedAt.toInstant().isAfter(now)
                || !expiresAt.toInstant().isAfter(issuedAt.toInstant())) {
            throw new JwtException("Invalid access token");
        }
    }

    public record AccessTokenClaims(String username, Long userId, int authVersion) {
    }
}
