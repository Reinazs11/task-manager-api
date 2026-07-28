package com.renan.taskmanager.common.observability;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.logstash.logback.argument.StructuredArguments;
import net.logstash.logback.encoder.LogstashEncoder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.slf4j.event.Level;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves that the {@code LogstashEncoder} configured in {@code logback-spring.xml}
 * for the {@code prod} profile emits the JSON contract we promise: correlation id
 * as a field, HTTP fields as fields, and the static service identity.
 *
 * <p><b>Why instantiate the encoder in isolation (and not capture from a running
 * appender)?</b> {@code ListAppender} captures the {@link ILoggingEvent} <em>before</em>
 * encoding — it never sees JSON. The only honest way to assert "the JSON output
 * contains field X" is to drive the encoder directly via {@code encode(event)},
 * which returns the serialized bytes. This test therefore wires the encoder the
 * same way {@code logback-spring.xml} does and asserts on the serialized JSON.</p>
 *
 * <p><b>Why mirror the XML config here instead of loading the file?</b> Loading the
 * XML would re-run Logback's full initialization against the test classpath, which
 * is brittle and re-tested by {@link StructuredLoggingProdProfileIT}. This unit test
 * asserts the <em>encoder contract</em>; the IT asserts the <em>wiring</em>. They
 * are complementary, not redundant. If the XML's encoder settings change, this
 * test MUST change — that coupling is the point.</p>
 */
class StructuredLoggingEncoderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private LoggerContext context;
    private LogstashEncoder encoder;

    @BeforeEach
    void setUp() {
        context = new LoggerContext();

        encoder = new LogstashEncoder();
        // Mirror logback-spring.xml's CONSOLE_JSON exactly.
        encoder.addIncludeMdcKeyName("requestId");
        encoder.addMdcKeyFieldName("requestId=correlationId");
        encoder.setIncludeStructuredArguments(true);
        encoder.setShortenedLoggerNameLength(36);
        encoder.setCustomFields("{\"application\":\"task-manager-api\",\"environment\":\"prod\"}");
        encoder.setContext(context);
        encoder.start();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
        encoder.stop();
        context.stop();
    }

    private JsonNode encode(ILoggingEvent event) throws Exception {
        byte[] bytes = encoder.encode(event);
        String json = new String(bytes, StandardCharsets.UTF_8).trim();
        return MAPPER.readTree(json);
    }

    private LoggingEvent event(String message, Object... args) {
        LoggingEvent event = new LoggingEvent();
        event.setLoggerContext(context);
        event.setLoggerName("com.renan.taskmanager.common.observability.SanitizingRequestLoggingFilter");
        event.setLevel(ch.qos.logback.classic.Level.INFO);
        event.setMessage(message);
        event.setArgumentArray(args);
        // The timestamp provider reads this long; without it the encoder NPEs on
        // Instant.getEpochSecond(). Real loggers set it in the LoggingEvent ctor.
        event.setTimeStamp(System.currentTimeMillis());
        // Snapshot MDC manually rather than via MDC.getCopyOfContextMap(): the
        // SLF4J MDC adapter is not initialized in a bare unit test (no Spring
        // boot), and the static call NPEs. We control exactly what's on the
        // event, mirroring what CorrelationIdFilter would have put there.
        Map<String, String> snapshot = new HashMap<>();
        String requestId = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (requestId != null) {
            snapshot.put(CorrelationIdFilter.MDC_KEY, requestId);
        }
        event.setMDCPropertyMap(snapshot);
        return event;
    }

    @Nested
    @DisplayName("Correlation id field")
    class CorrelationId {

        @Test
        @DisplayName("Should expose the requestId MDC value as correlationId in the JSON output")
        void shouldRenameRequestIdToCorrelationId() throws Exception {
            MDC.put(CorrelationIdFilter.MDC_KEY, "abc-123");
            LoggingEvent event = event("request");

            JsonNode json = encode(event);

            // The rename is the load-bearing claim: mdcKeyFieldName maps the
            // internal "requestId" key to the industry-standard "correlationId".
            assertThat(json.has("correlationId")).isTrue();
            assertThat(json.get("correlationId").asText()).isEqualTo("abc-123");
            // And the raw internal name must NOT leak — it would shadow the
            // canonical field in aggregators that index by exact name.
            assertThat(json.has("requestId")).isFalse();
        }

        @Test
        @DisplayName("Should omit correlationId when the MDC has no request id (e.g. startup logs)")
        void shouldOmitCorrelationIdWhenAbsent() throws Exception {
            LoggingEvent event = event("application started");

            JsonNode json = encode(event);

            assertThat(json.has("correlationId")).isFalse();
            assertThat(json.has("requestId")).isFalse();
        }
    }

    @Nested
    @DisplayName("Structured HTTP fields")
    class HttpFields {

        @Test
        @DisplayName("Should promote kv(...) args to first-class JSON fields")
        void shouldPromoteStructuredArguments() throws Exception {
            LoggingEvent event = event("{} {} -> {} ({} ms, {})",
                    StructuredArguments.kv("method", "POST"),
                    StructuredArguments.kv("uri", "/api/v1/projects"),
                    StructuredArguments.kv("status", 201),
                    StructuredArguments.kv("latencyMs", 42L),
                    StructuredArguments.kv("client", "10.0.0.1"));

            JsonNode json = encode(event);

            assertThat(json.get("method").asText()).isEqualTo("POST");
            assertThat(json.get("uri").asText()).isEqualTo("/api/v1/projects");
            assertThat(json.get("status").asInt()).isEqualTo(201);
            assertThat(json.get("latencyMs").asLong()).isEqualTo(42L);
            assertThat(json.get("client").asText()).isEqualTo("10.0.0.1");
        }
    }

    @Nested
    @DisplayName("Standard fields and service identity")
    class StandardFields {

        @Test
        @DisplayName("Should carry level, message and the static application/environment fields")
        void shouldCarryStandardAndCustomFields() throws Exception {
            LoggingEvent event = event("request");

            JsonNode json = encode(event);

            assertThat(json.get("@timestamp")).isNotNull();
            assertThat(json.get("level").asText()).isEqualTo(Level.INFO.toString());
            assertThat(json.get("message").asText()).isEqualTo("request");
            assertThat(json.get("application").asText()).isEqualTo("task-manager-api");
            assertThat(json.get("environment").asText()).isEqualTo("prod");
        }
    }
}
