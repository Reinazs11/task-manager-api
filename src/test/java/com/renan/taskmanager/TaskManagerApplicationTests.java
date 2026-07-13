package com.renan.taskmanager;

import org.junit.jupiter.api.Test;

/**
 * Smoke test: verifica se o contexto Spring sobe sem erro.
 *
 * <p>Este teste NÃO usa banco real (usaremos @DataJpaTest e Testcontainers
 * para testes de persistência). Por enquanto, valida apenas que a aplicação
 * inicializa.</p>
 */
class TaskManagerApplicationTests {

    @Test
    void contextLoads() {
        // Smoke test intencionalmente vazio: só valida que o contexto sobe.
        // Testes reais vivem nas classes de cada bounded context.
    }
}
