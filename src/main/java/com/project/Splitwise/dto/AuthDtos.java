package com.project.Splitwise.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Email String email,
            // Long enough to be worth hashing; no complexity theatre.
            @NotBlank @Size(min = 8, max = 100) String password,
            @NotBlank @Size(max = 100) String displayName) {
    }

    public record LoginRequest(
            @NotBlank String email,
            @NotBlank String password) {
    }

    /** The password hash is never part of any response. */
    public record AuthResponse(Long userId, String email, String displayName, String token) {
    }
}
