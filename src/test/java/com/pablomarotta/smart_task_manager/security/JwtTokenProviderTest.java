package com.pablomarotta.smart_task_manager.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    @Test
    void generateToken_ShouldReturnValidToken() {
        JwtTokenProvider provider = new JwtTokenProvider(SECRET, 3600000);
        provider.init();

        String token = provider.generateToken("testuser");

        assertNotNull(token);
        assertTrue(provider.validateToken(token));
    }

    @Test
    void validateToken_ShouldRejectInvalidToken() {
        JwtTokenProvider provider = new JwtTokenProvider(SECRET, 3600000);
        provider.init();

        String invalidToken = "invalid.token.value";

        assertFalse(provider.validateToken(invalidToken));
    }

    @Test
    void validateToken_ShouldRejectExpiredToken() {
        JwtTokenProvider provider = new JwtTokenProvider(SECRET, 3600000);
        provider.init();

        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        Date now = new Date();
        Date expired = new Date(now.getTime() - 1000);

        String token = Jwts.builder()
                .subject("testuser")
                .issuedAt(now)
                .expiration(expired)
                .signWith(key, Jwts.SIG.HS256)
                .compact();

        assertFalse(provider.validateToken(token));
    }

    @Test
    void getUsernameFromToken_ShouldExtractUsername() {
        JwtTokenProvider provider = new JwtTokenProvider(SECRET, 3600000);
        provider.init();

        String token = provider.generateToken("testuser");

        assertEquals("testuser", provider.getUsernameFromToken(token));
    }
}
