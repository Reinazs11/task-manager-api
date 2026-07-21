package com.renan.taskmanager.common.api;

import com.renan.taskmanager.tasks.domain.InvalidStatusTransitionException;
import com.renan.taskmanager.users.domain.InvalidCredentialsException;
import com.renan.taskmanager.users.domain.UserAlreadyExistsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

/**
 * Single source of truth for the API error contract.
 *
 * <p>Every exception that escapes a controller is normalized here into the
 * six-field {@link ErrorResponse} documented in {@code AGENTS.md}. There is no
 * "alternative" shape anywhere in the codebase: Spring Security 401s go through
 * {@link com.renan.taskmanager.common.security.JsonAuthenticationEntryPoint},
 * which produces the same bytes.</p>
 *
 * <p><b>Logging policy:</b>
 * <ul>
 *   <li>4xx (client errors) → {@code WARN}, no stack trace (the client misbehaved).</li>
 *   <li>5xx (server errors) → {@code ERROR} with the full throwable (we must see
 *       these in production to fix them).</li>
 *   <li>Generic {@link Exception} → never leaks internals to the client; the
 *       message is fixed to {@code "Unexpected error"} and the exception is
 *       logged in full server-side.</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String UNEXPECTED_ERROR_MESSAGE = "Unexpected error";

    // ===== Auth exceptions =====

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleUserAlreadyExists(UserAlreadyExistsException ex, WebRequest request) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage(), List.of(), request);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentials(InvalidCredentialsException ex, WebRequest request) {
        return buildResponse(HttpStatus.UNAUTHORIZED, ex.getMessage(), List.of(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, WebRequest request) {
        return buildResponse(HttpStatus.FORBIDDEN, ex.getMessage(), List.of(), request);
    }

    // ===== Tasks exceptions =====
    // Note: project/task lookup misses no longer raise a NotFoundException —
    // ownership is checked first via existsByIdAndOwnerId, so a miss collapses
    // to AccessDeniedException (→ 403) to prevent resource enumeration.

    @ExceptionHandler(InvalidStatusTransitionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidStatusTransition(InvalidStatusTransitionException ex, WebRequest request) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage(), List.of(), request);
    }

    // ===== Validation & malformed requests =====

    /**
     * Bean Validation failures. Captures <em>every</em> field error, not just the
     * first: clients can render all problems at once instead of round-tripping.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, WebRequest request) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .toList();
        return buildResponse(HttpStatus.BAD_REQUEST, "Validation failed", details, request);
    }

    /**
     * Malformed JSON body (e.g. unclosed object, invalid enum value). Spring wraps
     * the cause in this exception; we surface a stable client-facing message and
     * log the real cause server-side.
     */
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMalformedBody(
            org.springframework.http.converter.HttpMessageNotReadableException ex, WebRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, "Malformed request body", List.of(), request);
    }

    /**
     * {@code /api/v1/projects/not-a-uuid} → 400, not 500. Type mismatches are
     * client mistakes, not server bugs.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex, WebRequest request) {
        String message = "Invalid value for parameter '" + ex.getName() + "'";
        return buildResponse(HttpStatus.BAD_REQUEST, message, List.of(), request);
    }

    /**
     * Spring 6 raises this when no controller matches AND there is no static
     * resource either (e.g. a disabled springdoc endpoint, or a totally unknown
     * path). Mapping it to 404 — instead of letting it fall through to the 500
     * catch-all — keeps the contract intact for the "not found" class of errors.
     */
    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(
            org.springframework.web.servlet.resource.NoResourceFoundException ex, WebRequest request) {
        return buildResponse(HttpStatus.NOT_FOUND, "Resource not found", List.of(), request);
    }

    /**
     * Wrong HTTP verb on a known path (e.g. {@code PUT /api/v1/projects} when only
     * GET/POST are mapped). Surfaced with a stable message instead of Spring's
     * default error JSON, so the contract holds for every 4xx/5xx.
     */
    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(
            org.springframework.web.HttpRequestMethodNotSupportedException ex, WebRequest request) {
        String message = "Request method '" + ex.getMethod() + "' is not supported";
        return buildResponse(HttpStatus.METHOD_NOT_ALLOWED, message, List.of(), request);
    }

    // ===== Catch-all =====

    /**
     * Anything we did not anticipate. Never rethrows the original message: in
     * production, leaking internals (stack, class names, SQL fragments) is both
     * a security issue and a contract leak. We log the full throwable server-side.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, WebRequest request) {
        log.error("Unhandled exception on {} {}", pathOf(request), request, ex);
        ErrorResponse body = ErrorResponse.of(
                HttpStatus.INTERNAL_SERVER_ERROR,
                UNEXPECTED_ERROR_MESSAGE,
                pathOf(request),
                List.of()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    // ===== Helpers =====

    private ResponseEntity<ErrorResponse> buildResponse(HttpStatus status,
                                                        String message,
                                                        List<String> details,
                                                        WebRequest request) {
        if (status.is5xxServerError()) {
            log.error("Server error {}: {}", status.value(), message);
        } else {
            log.warn("Client error {}: {} ({})", status.value(), message, pathOf(request));
        }
        return ResponseEntity.status(status)
                .body(ErrorResponse.of(status, message, pathOf(request), details));
    }

    /**
     * Extracts just the request URI from the current web request.
     *
     * <p>We deliberately use {@code getRequestURI()} (not {@code getRequestURL()})
     * to avoid leaking query-string parameters into error logs and responses.</p>
     */
    private String pathOf(WebRequest request) {
        if (request instanceof ServletWebRequest servletWebRequest
                && servletWebRequest.getRequest() != null) {
            return servletWebRequest.getRequest().getRequestURI();
        }
        return null;
    }
}
