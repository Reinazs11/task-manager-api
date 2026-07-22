package com.renan.taskmanager.common.security;

import com.renan.taskmanager.common.api.HttpErrorWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Writes the standardized {@code ErrorResponse} JSON when Spring Security rejects
 * an unauthenticated request.
 *
 * <p><b>Why this exists (and is not just a lambda in {@link SecurityConfig}):</b>
 * Until Step 6 the security filter chain wrote its own ad-hoc JSON for 401
 * ({@code {"status":401,"error":"Unauthorized",...}}) that diverged from
 * {@link com.renan.taskmanager.common.api.GlobalExceptionHandler} — it lacked
 * {@code timestamp}, {@code error} reason phrase and {@code path}. Clients could
 * not rely on a single contract. Centralizing the entry point guarantees every
 * 401 — whether raised by Spring Security or by domain code — has the identical
 * six-field shape produced by {@link com.renan.taskmanager.common.api.ErrorResponse}.</p>
 *
 * <p><b>Serialization</b> is delegated to {@link HttpErrorWriter}, the same writer
 * used by the {@code @RestControllerAdvice} and by {@code RateLimitFilter}, so a
 * future change to the error bytes happens in exactly one place.</p>
 */
@Component
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

    static final String AUTHENTICATION_REQUIRED = "Authentication is required";

    private final HttpErrorWriter httpErrorWriter;

    public JsonAuthenticationEntryPoint(HttpErrorWriter httpErrorWriter) {
        this.httpErrorWriter = httpErrorWriter;
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        httpErrorWriter.write(response, HttpStatus.UNAUTHORIZED, AUTHENTICATION_REQUIRED, request.getRequestURI());
    }
}
