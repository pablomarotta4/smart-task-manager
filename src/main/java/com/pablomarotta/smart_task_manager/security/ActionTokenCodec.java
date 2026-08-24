package com.pablomarotta.smart_task_manager.security;

import com.pablomarotta.smart_task_manager.model.AccountActionPurpose;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
import java.util.UUID;

@Component
public class ActionTokenCodec {

    private static final int TOKEN_VERSION = 1;
    private static final int MINIMUM_SECRET_BYTES = 32;
    private static final String ACTION_ID_CLAIM = "aid";
    private static final String PURPOSE_CLAIM = "purpose";
    private static final String TOKEN_VERSION_CLAIM = "ver";

    private final SecretKey key;
    private final String issuer;
    private final String audience;
    private final Clock clock;

    public ActionTokenCodec(
            @Value("${account-action.token-secret}") String tokenSecret,
            @Value("${account-action.issuer}") String issuer,
            @Value("${account-action.audience}") String audience,
            Clock clock
    ) {
        byte[] secretBytes = tokenSecret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < MINIMUM_SECRET_BYTES) {
            throw new IllegalArgumentException("Account-action token secret must contain at least 256 bits");
        }
        this.key = Keys.hmacShaKeyFor(secretBytes);
        this.issuer = issuer;
        this.audience = audience;
        this.clock = clock;
    }

    public String encode(
            UUID actionId,
            AccountActionPurpose purpose,
            int tokenVersion,
            Instant issuedAt,
            Instant expiresAt
    ) {
        if (tokenVersion != TOKEN_VERSION) {
            throw new IllegalArgumentException("Unsupported account action token version");
        }
        if (!expiresAt.isAfter(issuedAt)) {
            throw new IllegalArgumentException("Account action token expiry must be after issuance");
        }
        return Jwts.builder()
                .header().type("account-action").and()
                .issuer(issuer)
                .audience().add(audience).and()
                .claim(ACTION_ID_CLAIM, actionId.toString())
                .claim(PURPOSE_CLAIM, purpose.name())
                .claim(TOKEN_VERSION_CLAIM, tokenVersion)
                .id(actionId.toString())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public DecodedActionToken decode(String compactToken, AccountActionPurpose expectedPurpose) {
        Jws<Claims> parsedToken = Jwts.parser()
                .verifyWith(key)
                .clock(() -> Date.from(Instant.now(clock)))
                .sig().add(Jwts.SIG.HS256).and()
                .build()
                .parseSignedClaims(compactToken);
        Claims claims = parsedToken.getPayload();
        validateClaims(
                claims,
                parsedToken.getHeader().getType(),
                parsedToken.getHeader().getAlgorithm(),
                expectedPurpose
        );
        UUID actionId = parseActionId(claims.get(ACTION_ID_CLAIM, String.class));
        return new DecodedActionToken(
                actionId,
                expectedPurpose,
                claims.get(TOKEN_VERSION_CLAIM, Integer.class),
                claims.getIssuedAt().toInstant(),
                claims.getExpiration().toInstant(),
                claims.getId()
        );
    }

    public String hash(String compactToken) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(compactToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void validateClaims(
            Claims claims,
            String tokenType,
            String algorithm,
            AccountActionPurpose expectedPurpose
    ) {
        if (!"account-action".equals(tokenType)
                || !"HS256".equals(algorithm)
                || !issuer.equals(claims.getIssuer())
                || claims.getAudience() == null
                || !claims.getAudience().contains(audience)
                || !expectedPurpose.name().equals(claims.get(PURPOSE_CLAIM, String.class))
                || !Integer.valueOf(TOKEN_VERSION).equals(claims.get(TOKEN_VERSION_CLAIM, Integer.class))
                || claims.get(ACTION_ID_CLAIM, String.class) == null
                || claims.getId() == null
                || claims.getIssuedAt() == null
                || claims.getExpiration() == null
                || !claims.get(ACTION_ID_CLAIM, String.class).equals(claims.getId())) {
            throw new JwtException("Invalid account action token");
        }
    }

    private UUID parseActionId(String actionId) {
        try {
            return UUID.fromString(actionId);
        } catch (IllegalArgumentException exception) {
            throw new JwtException("Invalid account action token", exception);
        }
    }

    public record DecodedActionToken(
            UUID actionId,
            AccountActionPurpose purpose,
            int tokenVersion,
            Instant issuedAt,
            Instant expiresAt,
            String tokenId
    ) {
    }
}
