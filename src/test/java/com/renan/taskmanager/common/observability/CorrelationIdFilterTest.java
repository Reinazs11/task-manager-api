package com.renan.taskmanager.common.observability;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link CorrelationIdFilter}.
 *
 * <p><b>Why pure unit tests (no Spring context)?</b>
 * The filter is a plain servlet {@code OncePerRequestFilter}; its behavior
 * depends only on the request and the MDC. Spinning up a Spring context would
 * slow the test by ~8s and test the framework, not the filter. We use Spring's
 * {@link MockHttpServletRequest}/{@link MockHttpServletResponse} (already on the
 * test classpath via spring-boot-starter-test) instead of mocking them
 * byte-for-byte — this is the idiomatic, low-ceremony way to test filters.</p>
 */
class CorrelationIdFilterTest {

    private CorrelationIdFilter filter;

    @BeforeEach
    void setUp() {
        filter = new CorrelationIdFilter();
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        // Belt-and-suspenders: never let one test's MDC leak into another.
        MDC.clear();
    }

    @Nested
    @DisplayName("When the client supplies X-Request-Id")
    class ClientSuppliedId {

        @Test
        @DisplayName("Should honor the incoming id and echo it back in the response header")
        void shouldHonorIncomingId() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader(CorrelationIdFilter.HEADER_NAME, "abc-123");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, mock(FilterChain.class));

            assertThat(response.getHeader(CorrelationIdFilter.HEADER_NAME)).isEqualTo("abc-123");
        }

        @Test
        @DisplayName("Should trim whitespace from the incoming id")
        void shouldTrimIncomingId() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader(CorrelationIdFilter.HEADER_NAME, "  abc-123  ");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, mock(FilterChain.class));

            assertThat(response.getHeader(CorrelationIdFilter.HEADER_NAME)).isEqualTo("abc-123");
        }
    }

    @Nested
    @DisplayName("When the client does not supply X-Request-Id")
    class GeneratedId {

        @Test
        @DisplayName("Should generate a valid UUID and echo it back")
        void shouldGenerateUuid() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, mock(FilterChain.class));

            String generated = response.getHeader(CorrelationIdFilter.HEADER_NAME);
            assertThat(generated).isNotNull();
            // Must be a real UUID — verifies it was minted, not null/empty.
            assertThatCode(() -> UUID.fromString(generated))
                    .as("generated X-Request-Id should be a valid UUID")
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should mint a fresh id when the incoming header is blank")
        void shouldMintWhenBlank() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader(CorrelationIdFilter.HEADER_NAME, "   ");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, mock(FilterChain.class));

            String generated = response.getHeader(CorrelationIdFilter.HEADER_NAME);
            assertThat(generated).isNotBlank();
            assertThatCode(() -> UUID.fromString(generated)).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("MDC lifecycle")
    class MdcLifecycle {

        @Test
        @DisplayName("Should populate the MDC for downstream filters, then clear it after")
        void shouldPopulateAndClearMdc() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader(CorrelationIdFilter.HEADER_NAME, "trace-it");
            MockHttpServletResponse response = new MockHttpServletResponse();

            // The chain captures the MDC mid-flight, then we assert it's cleared
            // once the filter returns.
            String[] seenDuringRequest = new String[1];
            FilterChain chain = (req, res) -> seenDuringRequest[0] = MDC.get(CorrelationIdFilter.MDC_KEY);

            filter.doFilter(request, response, chain);

            assertThat(seenDuringRequest[0])
                    .as("MDC must contain the id while the chain runs")
                    .isEqualTo("trace-it");
            assertThat(MDC.get(CorrelationIdFilter.MDC_KEY))
                    .as("MDC must be cleared after the chain returns (thread pool reuse)")
                    .isNull();
        }

        @Test
        @DisplayName("Should clear the MDC even when the chain throws")
        void shouldClearMdcOnException() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader(CorrelationIdFilter.HEADER_NAME, "boom-trace");
            MockHttpServletResponse response = new MockHttpServletResponse();

            FilterChain chain = (req, res) -> {
                throw new RuntimeException("simulated downstream failure");
            };

            assertThatThrownBy(() -> filter.doFilter(request, response, chain))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("simulated downstream failure");
            assertThat(MDC.get(CorrelationIdFilter.MDC_KEY))
                    .as("MDC must be cleared even when the chain throws (finally block)")
                    .isNull();
        }
    }
}
