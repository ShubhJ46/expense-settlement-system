package com.project.Splitwise.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey key;
    private final Duration ttl;

    /**
     * The secret comes from configuration with no default, so a deployment that forgets to
     * set it fails at startup rather than signing every token with a value that is sitting
     * in a public git history.
     */
    public JwtService(@Value("${splitwise.jwt.secret}") String secret,
                      @Value("${splitwise.jwt.ttl:PT12H}") Duration ttl) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException(
                    "splitwise.jwt.secret must be at least 32 bytes for HS256, was " + bytes.length);
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.ttl = ttl;
    }

    public String issue(Long userId, String email) {
        Instant now = Instant.now();
        return Jwts.builder()
                // The user id is the subject because it is what every authorization decision
                // is made against; email is carried only for readability in logs.
                .subject(String.valueOf(userId))
                .claim("email", email)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    /**
     * Returns the authenticated user id, or throws if the token is absent, expired, or not
     * signed by this service.
     */
    public Long parseUserId(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Long.valueOf(claims.getSubject());
        } catch (JwtException | NumberFormatException e) {
            throw new BadCredentialsException("Invalid or expired token", e);
        }
    }

    /** Narrow failure type so the filter does not have to catch a jjwt-specific exception. */
    public static class BadCredentialsException extends RuntimeException {
        public BadCredentialsException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
