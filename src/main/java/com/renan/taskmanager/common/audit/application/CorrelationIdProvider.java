package com.renan.taskmanager.common.audit.application;

import com.renan.taskmanager.common.observability.CorrelationIdFilter;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Bridges the request correlation id (held in the SLF4J {@link MDC} by
 * {@link CorrelationIdFilter}) into the application layer without forcing use
 * cases to depend on MDC directly.
 *
 * <p><b>Why a provider and not {@code MDC.get} inline in use cases?</b>
 * Reading the MDC statically inside a use case makes it impossible to unit
 * test: the MDC is empty outside a real HTTP request, so a test would have to
 * populate it to exercise the audit path. By routing through a component, unit
 * tests simply mock {@code CorrelationIdProvider} to return a fixed value, and
 * integration tests get the real value for free (the filter has already
 * populated the MDC on the request thread).</p>
 *
 * <p><b>Nullability:</b> returns {@link Optional#empty()} when there is no
 * correlation id — non-HTTP callers (a future scheduler, a CLI bootstrap) have
 * no request and therefore no id. The {@code audit_events.correlation_id}
 * column is nullable for exactly this case.</p>
 */
@Component
public class CorrelationIdProvider {

    /**
     * The current request's correlation id, or empty if the call is not bound
     * to an HTTP request.
     */
    public Optional<String> current() {
        return Optional.ofNullable(MDC.get(CorrelationIdFilter.MDC_KEY));
    }
}
