package com.renan.taskmanager.common.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.renan.taskmanager.common.api.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/**
 * Writes the standardized {@link ErrorResponse} JSON when Spring Security rejects
 * an unauthenticated request.
 *
 * <p><b>Why this exists (and is not just a lambda in {@link SecurityConfig}):</b>
 * Until Step 6 the security filter chain wrote its own ad-hoc JSON for 401
 * ({@code {"status":401,"error":"Unauthorized",...}}) that diverged from
 * {@link com.renan.taskmanager.common.api.GlobalExceptionHandler} — it lacked
 * {@code timestamp}, {@code error} reason phrase and {@code path}. Clients could
 * not rely on a single contract. Centralizing the entry point guarantees every
 * 401 — whether raised by Spring Security or by domain code — has the identical
 * six-field shape produced by {@link ErrorResponse}.</p>
 *
 * <p><b>Why an {@link ObjectMapper} bean instead of hand-concatenated JSON?</b>
 * String concatenation breaks the moment a field value contains a quote.
 * Delegating to Jackson keeps the bytes correct and consistent with the
 * serializer used everywhere else.</p>
 */
@Component
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final String AUTHENTICATION_REQUIRED = "Authentication is required";

    private final ObjectMapper objectMapper;

    public JsonAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        ErrorResponse body = ErrorResponse.of(
                HttpStatus.UNAUTHORIZED,
                AUTHENTICATION_REQUIRED,
                request.getRequestURI(),
                List.of()
        );
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(toJson(body));
    }

    private String toJson(ErrorResponse body) throws JsonProcessingException {
        return objectMapper.writeValueAsString(body);
    }
}
