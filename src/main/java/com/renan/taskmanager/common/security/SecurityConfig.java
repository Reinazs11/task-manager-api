package com.renan.taskmanager.common.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security configuration for stateless JWT authentication.
 *
 * <p><b>Why stateless?</b>
 * Each request carries its own auth (the JWT). No server-side session, no CSRF
 * token (CSRF targets session-based auth, irrelevant for stateless APIs). This
 * scales horizontally and is the standard for REST APIs in 2026.</p>
 *
 * <p><b>Why is BCryptPasswordEncoder exposed here too?</b>
 * Spring Security expects a {@link PasswordEncoder} bean for several features
 * (e.g. PasswordEncoderFactories). Our domain {@code BCryptPasswordHasher}
 * delegates to it internally; this bean makes it available to the framework.</p>
 *
 * <p><b>Public routes:</b> {@code /api/v1/auth/**} (register, login) and
 * API documentation ({@code /swagger-ui/**}, {@code /v3/api-docs/**}).
 * Everything else requires authentication.</p>
 */
@Configuration
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Stateless: no session is created or used
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // CSRF off: no cookies/sessions to protect. Standard for stateless REST APIs.
                .csrf(AbstractHttpConfigurer::disable)
                // Public vs protected routes
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .anyRequest().authenticated())
                // Register our JWT filter BEFORE the default form-login filter
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Exposes a BCrypt PasswordEncoder as a Spring bean.
     *
     * <p>Used by the framework internally and available to other components
     * that prefer to inject {@link PasswordEncoder} directly.</p>
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    /**
     * Exposes the AuthenticationManager for use cases that need it (e.g. login).
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
