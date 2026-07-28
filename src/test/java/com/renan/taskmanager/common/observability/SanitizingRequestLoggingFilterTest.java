package com.renan.taskmanager.common.observability;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.FilterChain;
import net.logstash.logback.argument.StructuredArgument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link SanitizingRequestLoggingFilter}.
 *
 * <p><b>Why capture logs with Logback's {@link ListAppender}?</b>
 * The whole point of this filter is that secrets never reach log output. The
 * only honest way to verify "X is not logged" is to capture what the logger
 * actually emitted and assert absence. Mocking the {@link Logger} would test the
 * mock, not the filter — so we attach an in-memory appender to the real logger.</p>
 *
 * <p><b>Structured arguments ({@code kv}):</b> the request line emits
 * {@code method}/{@code uri}/{@code status}/{@code latencyMs}/{@code client} as
 * {@link StructuredArgument}s. In dev each one renders as {@code key=value} in
 * the formatted message; in prod the {@code LogstashEncoder} promotes them to
 * first-class JSON fields (see {@code StructuredLoggingEncoderTest}). Both
 * behaviors are asserted here: the formatted string AND the fact that the args
 * are {@link StructuredArgument} instances (not plain strings).</p>
 */
class SanitizingRequestLoggingFilterTest {

    private SanitizingRequestLoggingFilter filter;
    private Logger logger;
    private ListAppender<ILoggingEvent> appender;
    private Level originalLevel;

    @BeforeEach
    void setUp() {
        filter = new SanitizingRequestLoggingFilter();
        logger = (Logger) LoggerFactory.getLogger(SanitizingRequestLoggingFilter.class);
        originalLevel = logger.getLevel();
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        // Enable DEBUG so the header-sanitization line is captured.
        logger.setLevel(Level.DEBUG);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        logger.setLevel(originalLevel);
    }

    private List<String> formattedMessages() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    private void runFilter(MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);
        filter.doFilter(request, response, mock(FilterChain.class));
    }

    @Nested
    @DisplayName("INFO request line")
    class RequestLine {

        @Test
        @DisplayName("Should emit method, URI, status, latency and client as key=value structured arguments")
        void shouldEmitStructuredRequestLine() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/projects");

            runFilter(request);

            // The formatted message renders each StructuredArgument as key=value.
            String line = formattedMessages().stream()
                    .filter(m -> m.contains("/api/v1/projects"))
                    .findFirst().orElseThrow(() -> new AssertionError("request line not logged"));
            assertThat(line).contains("method=POST");
            assertThat(line).contains("uri=/api/v1/projects");
            assertThat(line).contains("status=200");
            assertThat(line).contains("latencyMs=");
            assertThat(line).contains("client=");

            // The arguments MUST be StructuredArgument instances, not plain strings.
            // This is what lets the LogstashEncoder promote them to JSON fields.
            // (Asserting the contract, not just the rendered text.)
            ILoggingEvent event = appender.list.stream()
                    .filter(e -> e.getFormattedMessage().contains("/api/v1/projects"))
                    .findFirst().orElseThrow();
            Object[] args = event.getArgumentArray();
            assertThat(args).isNotNull();
            assertThat(Arrays.stream(args)).allMatch(arg -> arg instanceof StructuredArgument);
        }
    }

    @Nested
    @DisplayName("Header redaction")
    class HeaderRedaction {

        @Test
        @DisplayName("Should replace the Authorization header value with [REDACTED]")
        void shouldRedactAuthorizationHeader() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/projects");
            request.addHeader("Authorization", "Bearer super-secret-jwt-token");

            runFilter(request);

            List<String> messages = formattedMessages();
            // The sensitive token must NEVER appear in any captured log line.
            assertThat(messages).noneMatch(s -> s.contains("super-secret-jwt-token"));
            assertThat(messages).anyMatch(s -> s.contains("Authorization=[REDACTED]"));
        }

        @Test
        @DisplayName("Should redact Cookie and Set-Cookie headers too")
        void shouldRedactCookieHeaders() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/projects");
            request.addHeader("Cookie", "session=abc123");

            runFilter(request);

            assertThat(formattedMessages()).noneMatch(s -> s.contains("abc123"));
            assertThat(formattedMessages()).anyMatch(s -> s.contains("Cookie=[REDACTED]"));
        }

        @Test
        @DisplayName("Should preserve non-sensitive header values")
        void shouldKeepNonSensitiveHeaders() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/projects");
            request.addHeader("Accept", MediaType.APPLICATION_JSON_VALUE);
            request.addHeader("X-Request-Id", "trace-42");

            runFilter(request);

            assertThat(formattedMessages())
                    .anyMatch(s -> s.contains("Accept=application/json") && s.contains("X-Request-Id=trace-42"));
        }
    }

    @Nested
    @DisplayName("Redaction policy unit")
    class RedactionPolicy {

        @Test
        @DisplayName("Should classify Authorization, Cookie and Set-Cookie as sensitive (case-insensitive)")
        void shouldClassifySensitiveHeaders() {
            assertThat(SanitizingRequestLoggingFilter.isSensitive("Authorization")).isTrue();
            assertThat(SanitizingRequestLoggingFilter.isSensitive("authorization")).isTrue();
            assertThat(SanitizingRequestLoggingFilter.isSensitive("AUTHORIZATION")).isTrue();
            assertThat(SanitizingRequestLoggingFilter.isSensitive("Cookie")).isTrue();
            assertThat(SanitizingRequestLoggingFilter.isSensitive("Set-Cookie")).isTrue();
        }

        @Test
        @DisplayName("Should NOT classify arbitrary headers as sensitive")
        void shouldNotClassifyArbitraryHeaders() {
            assertThat(SanitizingRequestLoggingFilter.isSensitive("Accept")).isFalse();
            assertThat(SanitizingRequestLoggingFilter.isSensitive("Content-Type")).isFalse();
            assertThat(SanitizingRequestLoggingFilter.isSensitive("X-Request-Id")).isFalse();
        }
    }
}
