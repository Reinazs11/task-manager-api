package com.renan.taskmanager.common.audit.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link CorrelationIdProvider}.
 *
 * <p>This is one of the few places where touching the real SLF4J {@link MDC}
 * in a unit test is appropriate: the provider is a thin wrapper over
 * {@code MDC.get}, so we exercise the real MDC and clean up in
 * {@link AfterEach} to avoid leaking state to other tests on the same thread
 * (mirroring the discipline of {@code CorrelationIdFilter}).</p>
 */
class CorrelationIdProviderTest {

    private final CorrelationIdProvider provider = new CorrelationIdProvider();

    @AfterEach
    void clearMdc() {
        MDC.remove("requestId");
    }

    @Nested
    @DisplayName("current()")
    class Current {

        @Test
        @DisplayName("When MDC has a requestId, it is returned")
        void shouldReturnMdcValue() {
            MDC.put("requestId", "req-123");

            assertThat(provider.current()).contains("req-123");
        }

        @Test
        @DisplayName("When MDC has no requestId, returns empty")
        void shouldReturnEmptyWhenAbsent() {
            MDC.remove("requestId");

            assertThat(provider.current()).isEmpty();
        }
    }
}
