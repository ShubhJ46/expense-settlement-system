package com.project.Splitwise.service;

import com.project.Splitwise.dto.AuthDtos;
import com.project.Splitwise.model.User;
import com.project.Splitwise.repository.UserRepository;
import com.project.Splitwise.security.JwtService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthDtos.AuthResponse register(AuthDtos.RegisterRequest req) {
        if (users.existsByEmailIgnoreCase(req.email())) {
            throw new IllegalArgumentException("Email is already registered");
        }

        User user = new User();
        user.setEmail(req.email().toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setDisplayName(req.displayName());

        try {
            User saved = users.save(user);
            return response(saved);
        } catch (DataIntegrityViolationException e) {
            // Two concurrent registrations for the same address: the unique index is the
            // authority, not the existsBy check above, which can be stale by the time we save.
            throw new IllegalArgumentException("Email is already registered", e);
        }
    }

    /**
     * Verifies credentials and issues a token.
     *
     * <p>An unknown email and a wrong password produce the same failure on purpose, so the
     * endpoint cannot be used to discover which addresses have accounts.
     */
    @Transactional(readOnly = true)
    public AuthDtos.AuthResponse login(AuthDtos.LoginRequest req) {
        User user = users.findByEmailIgnoreCase(req.email())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }

        return response(user);
    }

    private AuthDtos.AuthResponse response(User user) {
        return new AuthDtos.AuthResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                jwtService.issue(user.getId(), user.getEmail()));
    }
}
