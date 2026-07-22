package com.renan.taskmanager.common.api;

import com.renan.taskmanager.common.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for the {@code /actuator/health} probe.
 *
 * <p><b>Why this test exists:</b> the endpoint is referenced by
 * {@code docker-compose.yml}'s healthcheck and is the readiness/liveness
 * signal a reviewer expects in a Dockerized Spring Boot app. Two things can
 * silently break it: (1) Spring Security locking it down behind a JWT, or
 * (2) the actuator exposure config leaking more than {@code health}. Both
 * are caught here without sending any token.</p>
 */
class ActuatorHealthIT extends AbstractIntegrationTest {

    @Test
    @DisplayName("GET /actuator/health without token returns 200 and UP status")
    void shouldReturnUpWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
