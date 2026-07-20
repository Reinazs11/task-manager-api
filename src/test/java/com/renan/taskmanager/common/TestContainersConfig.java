package com.renan.taskmanager.common;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Single source of truth for the PostgreSQL Testcontainer used by every
 * integration test.
 *
 * <p><b>Why expose the container as a {@code @Bean} (and not as a
 * {@code @Container} static field on the test class)?</b>
 * A static {@code @Container} field is started/stopped by the Testcontainers
 * JUnit extension on a per-class basis. When multiple test classes share the
 * same Spring ApplicationContext (via TestContext cache), the container started
 * by class A is torn down when A finishes — but the cached context still holds
 * a reference to it, so class B reconnects to a dead container and fails with
 * {@code Connection refused} after the JPA timeout.</p>
 *
 * <p>Defining the container as a {@code @Bean} moves its lifecycle into Spring:
 * the container lives exactly as long as the {@code ApplicationContext} that
 * owns it. Spring TestContext caches that context across classes with identical
 * configuration, so the container is started once and reused — no race, no
 * premature teardown. This is the pattern recommended by Spring Boot 3.1+.</p>
 *
 * <p><b>Why {@code @ServiceConnection}?</b>
 * It auto-wires the container's JDBC URL/credentials into the context (replacing
 * the manual {@code @DynamicPropertySource} boilerplate), so the app's
 * {@code DataSource} points at the container without any extra config.</p>
 *
 * <p><b>Why {@code proxyBeanMethods = false}?</b>
 * {@code @TestConfiguration} is a lightweight config class; CGLIB proxying is
 * unnecessary here and disabling it avoids a minor startup cost and a possible
 * Spring Boot warning.</p>
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestContainersConfig {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresqlContainer() {
        return new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("taskmanager_test")
                .withUsername("test")
                .withPassword("test");
    }
}
