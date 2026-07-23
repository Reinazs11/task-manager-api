package com.renan.taskmanager.common.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/**
 * Single way to serialize an {@link ErrorResponse} into an
 * {@link HttpServletResponse}, used by every layer that rejects a request
 * <em>outside</em> the {@code @RestControllerAdvice} (servlet filters and the
 * Spring Security entry point).
 *
 * <p><b>Why this exists.</b> Before this helper, the six-field contract was
 * honored by copy-paste in three places: {@code GlobalExceptionHandler},
 * {@code JsonAuthenticationEntryPoint}, and {@code RateLimitFilter}. A future
 * change to the shape (new field, encoding tweak, extra header) would have to
 * be remembered in all three — and forgetting one silently breaks the contract
 * for a subset of status codes. Centralizing the bytes here makes the contract
 * enforced by construction, not by convention.</p>
 *
 * <p><b>Scope:</b> this writes status, content-type/encoding, and the JSON body.
 * Callers that need status-specific headers (e.g. {@code Retry-After} on a 429)
 * set them on the response <em>before</em> calling {@link #write}, so the common
 * path stays shared while extra needs remain explicit at the call site.</p>
 */
@Component
public final class HttpErrorWriter {

    private final ObjectMapper objectMapper;

    public HttpErrorWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Writes the standardized error body for the given status.
     *
     * <p>Sets status, {@code Content-Type: application/json; charset=UTF-8}, and
     * writes the {@link ErrorResponse} JSON. The caller may attach extra headers
     * (e.g. {@code Retry-After}) before calling this method.</p>
     *
     * @param response the servlet response to write to
     * @param status   the HTTP status to return
     * @param message  short human-readable message safe for clients
     * @param path     request URI that failed (never the full URL with query string)
     * @throws IOException if writing the response fails
     */
    public void write(HttpServletResponse response, HttpStatus status, String message, String path) throws IOException {
        write(response, status, message, path, List.of());
    }

    /**
     * Variant that includes field-level {@code details} (used for validation).
     */
    public void write(HttpServletResponse response, HttpStatus status, String message, String path,
                      List<String> details) throws IOException {
        ErrorResponse body = ErrorResponse.of(status, message, path, details);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
