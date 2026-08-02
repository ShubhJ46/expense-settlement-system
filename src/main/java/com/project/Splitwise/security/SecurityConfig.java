package com.project.Splitwise.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtFilter) {
        this.jwtFilter = jwtFilter;
    }

    /**
     * Everything is authenticated unless it is explicitly opened.
     *
     * <p>This is deliberately the inverse of the previous configuration, which listed the
     * business endpoints as {@code permitAll} and left {@code anyRequest().authenticated()}
     * covering nothing that mattered — any caller could post an expense to any group as any
     * user. Denying by default means a new controller is protected on the day it is written
     * rather than on the day somebody notices.
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // No cookies are issued, so there is no session for a cross-site form to ride on.
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session ->
                    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/auth/register", "/auth/login").permitAll()
                    // Actuator is served only on management.server.port, never on the
                    // application port, so permitting the path here exposes nothing: a
                    // request to /actuator/** on the API port has no handler and 404s.
                    //
                    // It is still required. This filter chain is applied to the management
                    // port as well, and EndpointRequest.toAnyEndpoint() deliberately does not
                    // match once actuator has been moved to its own port — so a dedicated
                    // actuator chain silently never matches, and without this rule every
                    // Prometheus scrape gets a 401 from anyRequest().authenticated().
                    .requestMatchers("/actuator/**").permitAll()
                    .anyRequest().authenticated()
            )
            // 401 with an empty body rather than a redirect to a login page that does not exist.
            .exceptionHandling(ex ->
                    ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
