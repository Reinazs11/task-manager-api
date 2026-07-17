package com.renan.taskmanager.common.api;

import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;

/**
 * Immutable, single-shape error body returned by every failure path in the API.
 *
 * <p><b>Why a record (and not a {@code Map&lt;String,Object&gt;})?</b>
 * The old implementation built a {@link java.util.Map} per request, which is
 * untyped and silently accepts typos in keys. A record pins the contract:
 * any code path that forgets one of the six fields will not compile. It is also
 * serialized identically by Jackson, so clients keep seeing the same JSON.</p>
 *
 * <p><b>The contract (do not change without a major version bump):</b>
 * <pre>
 * {
 *   "timestamp": "2026-07-17T12:00:00Z",
 *   "status": 409,
 *   "error": "Conflict",
 *   "message": "...",
 *   "path": "/api/v1/projects/abc",
 *   "details": ["field: reason", ...]
 * }
 * </pre>
 *
 * <ul>
 *   <li>{@code timestamp} — server-side UTC instant, ISO-8601.</li>
 *   <li>{@code status} / {@code error} — HTTP code and reason phrase.</li>
 *   <li>{@code message} — short human message safe to show to clients.</li>
 *   <li>{@code path} — request URI that failed (never the full URL with query
 *       string, to avoid leaking parameters).</li>
 *   <li>{@code details} — never null; validation paths fill it with one entry
 *       per offending field, other paths leave it empty.</li>
 * </ul>
 */
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<String> details
) {

    /**
     * Canonical factory: builds an {@link ErrorResponse} for the given status,
     * computing {@code timestamp} and {@code error} from the HTTP status.
     *
     * @param status  the HTTP status to return
     * @param message short human-readable message
     * @param path    request URI (may be {@code null} when no request context)
     * @param details field-level details; empty list when there are none
     */
    public static ErrorResponse of(HttpStatus status, String message, String path, List<String> details) {
        return new ErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                path,
                details == null ? List.of() : List.copyOf(details)
        );
    }
}
