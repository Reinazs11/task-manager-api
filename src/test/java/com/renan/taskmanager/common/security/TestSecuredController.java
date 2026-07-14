package com.renan.taskmanager.common.security;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test-only controller that requires authentication (matches
 * {@code anyRequest().authenticated()} in {@link SecurityConfig}).
 *
 * <p><b>Why a dedicated test controller?</b>
 * We don't have protected endpoints in the main code yet (they arrive in Step 5
 * with Tasks). But we need to verify that the security infrastructure
 * (filter + config) actually enforces authentication NOW, not later. This
 * controller lives under {@code src/test} so it never ships in the JAR.</p>
 *
 * <p>It's picked up by {@code @SpringBootTest} during context scan because the
 * base package {@code com.renan.taskmanager} is covered by
 * {@code @SpringBootApplication}'s default scan.</p>
 */
@RestController
public class TestSecuredController {

    @GetMapping("/api/v1/test/secure")
    public String secure() {
        return "authenticated";
    }
}
