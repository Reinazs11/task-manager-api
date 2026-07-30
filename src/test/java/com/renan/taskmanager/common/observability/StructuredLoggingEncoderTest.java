package com.renan.taskmanager.common.observability;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import org.springframework.boot.logging.logback.StructuredLogEncoder;
import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.event.KeyValuePair;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the JSON contract produced by Spring Boot's native Logstash encoder.
 */
class StructuredLoggingEncoderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private LoggerContext context;
    private StructuredLogEncoder encoder;

    @BeforeEach
    void setUp() {
        context = new LoggerContext();
        context.putObject(Environment.class.getName(), new StandardEnvironment());
        encoder = new StructuredLogEncoder();
        encoder.setFormat("logstash");
        encoder.setContext(context);
        encoder.start();
    }

    @AfterEach
    void tearDown() {
        encoder.stop();
        context.stop();
    }

    private JsonNode encode(LoggingEvent event) {
        byte[] bytes = encoder.encode(event);
        String json = new String(bytes, StandardCharsets.UTF_8).trim();
        return MAPPER.readTree(json);
    }

    private LoggingEvent event(String message) {
        return event(message, Map.of());
    }

    private LoggingEvent event(String message, Map<String, String> mdc) {
        LoggingEvent event = new LoggingEvent();
        event.setLoggerContext(context);
        event.setLoggerName("com.renan.taskmanager.common.observability.SanitizingRequestLoggingFilter");
        event.setLevel(ch.qos.logback.classic.Level.INFO);
        event.setMessage(message);
        event.setTimeStamp(System.currentTimeMillis());
        event.setMDCPropertyMap(mdc);
        return event;
    }

    @Test
    @DisplayName("Should expose the request correlation id from MDC")
    void shouldExposeCorrelationId() {
        LoggingEvent event = event(
                "request", Map.of(CorrelationIdFilter.MDC_KEY, "abc-123"));

        JsonNode json = encode(event);

        assertThat(json.get("requestId").asString()).isEqualTo("abc-123");
    }

    @Test
    @DisplayName("Should promote SLF4J key-value pairs to first-class JSON fields")
    void shouldPromoteStructuredFields() {
        LoggingEvent event = event("HTTP request completed");
        event.setKeyValuePairs(List.of(
                new KeyValuePair("method", "POST"),
                new KeyValuePair("status", 201),
                new KeyValuePair("latencyMs", 42L)));

        JsonNode json = encode(event);

        assertThat(json.get("method").asString()).isEqualTo("POST");
        assertThat(json.get("status").asInt()).isEqualTo(201);
        assertThat(json.get("latencyMs").asLong()).isEqualTo(42L);
    }

    @Test
    @DisplayName("Should carry timestamp, level and message")
    void shouldCarryStandardFields() {
        JsonNode json = encode(event("request"));

        assertThat(json.get("@timestamp")).isNotNull();
        assertThat(json.get("level").asString()).isEqualTo("INFO");
        assertThat(json.get("message").asString()).isEqualTo("request");
    }
}
