package com.project.Splitwise.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtServiceTest {

    private static final String SECRET = "a-test-signing-key-that-is-long-enough-32";

    private static JwtService service() {
        return new JwtService(SECRET, Duration.ofHours(12));
    }

    @Test
    void roundTripsTheUserId() {
        JwtService service = service();
        assertEquals(42L, service.parseUserId(service.issue(42L, "a@example.test")));
    }

    @Test
    @DisplayName("a token signed with a different key is rejected")
    void rejectsForeignSignature() {
        String foreign = new JwtService("a-completely-different-key-also-32-bytes", Duration.ofHours(1))
                .issue(42L, "a@example.test");

        assertThrows(JwtService.BadCredentialsException.class, () -> service().parseUserId(foreign));
    }

    @Test
    @DisplayName("an expired token is rejected")
    void rejectsExpiredToken() {
        // Negative TTL puts expiry in the past the moment it is issued.
        JwtService expiring = new JwtService(SECRET, Duration.ofSeconds(-60));
        String token = expiring.issue(42L, "a@example.test");

        assertThrows(JwtService.BadCredentialsException.class, () -> service().parseUserId(token));
    }

    @Test
    @DisplayName("a tampered payload is rejected rather than silently trusted")
    void rejectsTamperedToken() {
        JwtService service = service();
        String token = service.issue(42L, "a@example.test");

        // Flip a character in the payload segment; the signature no longer matches.
        String[] parts = token.split("\\.");
        parts[1] = parts[1].substring(0, parts[1].length() - 2)
                + (parts[1].endsWith("A") ? "B" : "A");
        String tampered = String.join(".", parts);

        assertNotEquals(token, tampered);
        assertThrows(JwtService.BadCredentialsException.class, () -> service.parseUserId(tampered));
    }

    @Test
    void rejectsGarbage() {
        assertThrows(JwtService.BadCredentialsException.class, () -> service().parseUserId("not.a.jwt"));
    }

    @Test
    @DisplayName("a secret too short for HS256 fails fast at construction")
    void refusesWeakSecret() {
        assertThrows(IllegalStateException.class, () -> new JwtService("too-short", Duration.ofHours(1)));
    }
}
