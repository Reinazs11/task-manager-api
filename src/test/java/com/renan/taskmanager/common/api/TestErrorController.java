package com.renan.taskmanager.common.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test-only controller that always blows up, used by {@link ErrorResponseContractIT}
 * to exercise the catch-all {@code @ExceptionHandler(Exception.class)} → 500 path.
 *
 * <p><b>Why a dedicated fixture instead of forcing an NPE somewhere in real
 * code?</b> Because the failure must be deterministic and isolated — flaky,
 * environment-dependent failures in production code are exactly the kind of test
 * smell we are trying to avoid. The endpoint requires authentication
 * ({@code anyRequest().authenticated()}), so callers must pass a valid JWT.</p>
 *
 * <p>Lives under {@code src/test}; never packaged into the shipped JAR.</p>
 */
@RestController
public class TestErrorController {

    /**
     * Throws a synthetic {@link IllegalStateException} carrying a sensitive-looking
     * payload. The contract test asserts this payload does <em>not</em> leak into
     * the client-facing response.
     */
    @GetMapping("/api/v1/test/_500")
    public String boom() {
        throw new IllegalStateException("INTERNAL_SECRET_DO_NOT_LEAK: db password=hunter2");
    }
}
