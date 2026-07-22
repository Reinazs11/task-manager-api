package com.renan.taskmanager.common.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Spring Security configuration for stateless JWT authentication.
 *
 * <p><b>Why stateless?</b>
 * Each request carries its own auth (the JWT). No server-side session, no CSRF
 * token (CSRF targets session-based auth, irrelevant for stateless APIs). This
 * scales horizontally and is the standard for REST APIs in 2026.</p>
 *
 * <p><b>Why expose the {@link PasswordEncoder} as a bean here?</b>
 * Spring Security expects one for several framework features
 * (e.g. PasswordEncoderFactories), and our domain {@code BCryptPasswordHasher}
 * injects the same bean — single source of truth for the cost factor.</p>
 *
 * <p><b>Public routes:</b> {@code /api/v1/auth/**} (register, login) and
 * API documentation ({@code /swagger-ui/**}, {@code /v3/api-docs/**}).
 * Everything else requires authentication.</p>
 *
 * <p><b>CORS:</b> the dev profile ships a default origin
 * ({@code http://localhost:3000}). In production the {@code prod} profile
 * leaves {@code app.cors.allowed-origins} empty on purpose, so the app fails
 * fast at startup unless {@code CORS_ALLOWED_ORIGINS} is set — never silently
 * allows any origin.</p>
 */
@Configuration
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JsonAuthenticationEntryPoint authenticationEntryPoint;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          JsonAuthenticationEntryPoint authenticationEntryPoint) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Stateless: no session is created or used
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // CSRF off: no cookies/sessions to protect. Standard for stateless REST APIs.
                .csrf(AbstractHttpConfigurer::disable)
                // CORS: uses the CorsConfigurationSource bean below. Browser-based SPAs
                // (Vite, React dev server) need this or preflight fails on every authed call.
                .cors(Customizer.withDefaults())
                // 401 responses go through JsonAuthenticationEntryPoint so they share the
                // same six-field ErrorResponse shape produced by GlobalExceptionHandler.
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint(authenticationEntryPoint))
                // Public vs protected routes
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        // Health probe for Docker/K8s — must not require JWT.
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .anyRequest().authenticated())
                // Register our JWT filter BEFORE the default form-login filter
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Allowed origins for browser-based (credentialed) requests.
     *
     * <p>Split on comma so {@code CORS_ALLOWED_ORIGINS=https://a.com,https://b.com}
     * works in a single env var. Empty values are filtered out.</p>
     *
     * <p><b>Fail-fast:</b> if the resolved list is empty, we throw rather than
     * silently allowing all origins (which would be the default-Cors behavior).</p>
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins:}") String allowedOriginsCsv) {
        List<String> origins = Arrays.stream(allowedOriginsCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        if (origins.isEmpty()) {
            throw new IllegalStateException(
                    "app.cors.allowed-origins is empty. Set CORS_ALLOWED_ORIGINS (comma-separated) "
                            + "before starting the application.");
        }

        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(origins);
        cfg.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Request-Id"));
        cfg.setExposedHeaders(List.of("X-Request-Id"));
        // Required when origins are explicit (non-*) and the client sends Authorization.
        cfg.setAllowCredentials(true);
        // Cache preflight in the browser for 30 min to cut OPTIONS traffic.
        cfg.setMaxAge(1800L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }

    /**
     * Exposes a BCrypt PasswordEncoder as a Spring bean.
     *
     * <p><b>Single source of truth:</b> {@code BCryptPasswordHasher} injects
     * this bean rather than constructing its own encoder, so the cost factor
     * lives in exactly one place.</p>
     *
     * <p><b>Cost 12:</b> aligns with OWASP 2026 recommendations. Each cost
     * point doubles compute time; 12 is the floor for new deployments.</p>
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /**
     * Exposes the AuthenticationManager for use cases that need it (e.g. login).
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
