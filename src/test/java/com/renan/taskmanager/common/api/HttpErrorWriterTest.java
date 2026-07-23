package com.renan.taskmanager.common.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link HttpErrorWriter}.
 *
 * <p>The writer is the single point that serializes {@link ErrorResponse} into
 * a servlet response for every rejection path <em>outside</em> the
 * {@code @RestControllerAdvice} (the 429 rate-limit filter and the 401 security
 * entry point). Because its whole reason to exist is "one place to enforce the
 * contract", it earns a direct unit test instead of relying solely on the ITs
 * that happen to exercise it.</p>
 */
class HttpErrorWriterTest {

    // Mirrors the Spring Boot-configured mapper (which auto-registers JSR310 so
    // ErrorResponse#timestamp, an Instant, serializes correctly). A bare
    // ObjectMapper would fail on the date/time type.
    private final HttpErrorWriter writer = new HttpErrorWriter(new ObjectMapper().registerModule(new JavaTimeModule()));

    @Test
    @DisplayName("Should write the status, JSON content-type and the six-field body")
    void shouldWriteStandardErrorEnvelope() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        writer.write(response, HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded", "/api/v1/auth/login");

        assertThat(response.getStatus()).isEqualTo(429);
        // Content-Type carries the charset because setCharacterEncoding is applied
        // after setContentType — that is the intended behavior (valid JSON + UTF-8).
        assertThat(response.getContentType()).isEqualTo("application/json;charset=UTF-8");

        String body = response.getContentAsString();
        assertThat(body).contains("\"status\":429");
        assertThat(body).contains("\"error\":\"Too Many Requests\"");
        assertThat(body).contains("\"message\":\"Rate limit exceeded\"");
        assertThat(body).contains("\"path\":\"/api/v1/auth/login\"");
        assertThat(body).contains("\"timestamp\"");
        assertThat(body).contains("\"details\":[]");
    }

    @Test
    @DisplayName("Should include field-level details when provided")
    void shouldIncludeDetailsWhenProvided() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        writer.write(response, HttpStatus.BAD_REQUEST, "Validation failed", "/x",
                List.of("email: must not be blank"));

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("\"email: must not be blank\"");
    }

    @Test
    @DisplayName("Should preserve headers set by the caller before write (e.g. Retry-After)")
    void shouldPreserveCallerHeaders() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setHeader("Retry-After", "12");

        writer.write(response, HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded", "/x");

        assertThat(response.getHeader("Retry-After")).isEqualTo("12");
    }
}
