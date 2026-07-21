package com.renan.taskmanager;

import com.renan.taskmanager.common.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

/**
 * Smoke test: verifies that the full Spring application context boots without
 * error against a real PostgreSQL container.
 *
 * <p><b>Why extend {@link AbstractIntegrationTest} (and not stay plain)?</b>
 * Before this change the class had only {@code @Test} and no
 * {@code @SpringBootTest} — so {@code contextLoads()} was not actually loading
 * any context, defeating the purpose of a smoke test. Inheriting the base class
 * boots the real application (web layer, security, JPA, mappers, observability
 * filters) against the shared Testcontainer, so a wiring regression
 * (e.g. a broken {@code @Bean}, a missing import) fails here loudly instead of
 * only surfacing in a focused test much later.</p>
 *
 * <p>Test body stays intentionally empty: the assertion is "the context loads".
 * Detailed behavior lives in the bounded-context tests.</p>
 */
class TaskManagerApplicationIT extends AbstractIntegrationTest {

    @Test
    void contextLoads() {
        // Smoke test intencionalmente vazio: só valida que o contexto sobe.
        // Testes reais vivem nas classes de cada bounded context.
    }
}
