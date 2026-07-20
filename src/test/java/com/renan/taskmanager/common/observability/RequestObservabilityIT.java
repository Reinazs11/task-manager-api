package com.renan.taskmanager.common.observability;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.renan.taskmanager.common.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end verification that the observability filters are wired into the
 * Spring Security filter chain and that the correlation id survives the whole
 * request lifecycle.
 *
 * <p><b>What this locks down (that the unit tests cannot):</b>
 * <ul>
 *   <li>The filter is actually registered (a forgotten {@code @Component} would
 *       silently remove it).</li>
 *   <li>The id is present in the response header after the security entry point
 *       runs — proving the filter executes BEFORE security, so even rejected
 *       requests are traceable.</li>
 *   <li>The id reaches the MDC consumed by the Logback pattern, i.e. a log line
 *       emitted during the request contains the id.</li>
 * </ul>
 * The unit tests verify the filter in isolation; this test verifies the wiring.</p>
 */
class RequestObservabilityIT extends AbstractIntegrationTest {

    private Logger requestLogLogger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void attachAppender() {
        // Attach to the request-logging filter's logger so we can observe what
        // it emits during the request.
        requestLogLogger = (Logger) LoggerFactory.getLogger(SanitizingRequestLoggingFilter.class);
        appender = new ListAppender<>();
        appender.start();
        requestLogLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        if (requestLogLogger != null && appender != null) {
            requestLogLogger.detachAppender(appender);
        }
    }

    @Test
    @DisplayName("Should echo the client X-Request-Id in the response, even for rejected (401) requests")
    void shouldEchoCorrelationIdEvenWhenRejected() throws Exception {
        // No Authorization header → security entry point returns 401. The filter
        // must still have stamped the response with X-Request-Id, otherwise
        // unauthenticated requests would be untraceable.
        MvcResult result = mockMvc.perform(get("/api/v1/projects")
                        .header(CorrelationIdFilter.HEADER_NAME, "trace-e2e-001"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(CorrelationIdFilter.HEADER_NAME, "trace-e2e-001"))
                .andReturn();

        // The request-logging filter must have emitted a line for this request
        // (proves it ran), and the correlation id must be present in the event's
        // MDC map (proves the CorrelationIdFilter populated it upstream, since
        // the pattern would print %X{requestId} from the MDC, not from the
        // message itself).
        assertThat(appender.list)
                .as("request-logging filter should emit at least one line for the request")
                .isNotEmpty();

        boolean anyEventCarriedTheId = appender.list.stream()
                .map(event -> event.getMDCPropertyMap())
                .filter(java.util.Objects::nonNull)
                .anyMatch(mdc -> "trace-e2e-001".equals(mdc.get(CorrelationIdFilter.MDC_KEY)));
        assertThat(anyEventCarriedTheId)
                .as("at least one log event must carry the correlation id in its MDC; "
                        + "this proves CorrelationIdFilter ran before SanitizingRequestLoggingFilter "
                        + "(wiring order) and populated the MDC")
                .isTrue();

        // And at least one event's message should reference the URI we hit.
        boolean anyMessageReferencesUri = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .anyMatch(m -> m.contains("/api/v1/projects"));
        assertThat(anyMessageReferencesUri)
                .as("at least one log line should mention the requested URI")
                .isTrue();
    }
}
