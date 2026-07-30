package com.renan.taskmanager.common.observability;

import com.renan.taskmanager.common.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the production profile selects Spring Boot's native Logstash
 * structured logging format.
 */
@SpringBootTest(properties = {
        "spring.profiles.active=prod",
        // Prod profile ships CORS_ALLOWED_ORIGINS empty on purpose (fail-fast).
        // Supply a value so the ApplicationContext can start; this test does not
        // exercise CORS.
        "app.cors.allowed-origins=https://prod.example.com"
})
class StructuredLoggingProdProfileIT extends AbstractIntegrationTest {

    @Autowired
    private Environment environment;

    @Test
    @DisplayName("Prod should use native Logstash JSON with service identity")
    void prodShouldUseNativeStructuredLogging() {
        assertThat(environment.getProperty("logging.structured.format.console"))
                .isEqualTo("logstash");
        assertThat(environment.getProperty("logging.structured.json.add.service.name"))
                .isEqualTo("task-manager-api");
        assertThat(environment.getProperty("logging.structured.json.add.deployment.environment"))
                .isEqualTo("prod");
    }
}
