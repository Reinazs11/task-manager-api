package com.renan.taskmanager.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.renan.taskmanager.common.ratelimit.RateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Base class for every {@code @SpringBootTest}-based integration test.
 *
 * <p><b>Why a shared base (and not the container declared in each class)?</b>
 * Every test class that declared its own {@code PostgreSQLContainer} produced
 * a duplicated, potentially conflicting container. Concentrating the wiring
 * here (via {@link TestContainersConfig}) removes the boilerplate and makes the
 * "we always test against real PostgreSQL" intent explicit in one place.</p>
 *
 * <p><b>Why PostgreSQL 16-alpine and not H2?</b>
 * H2 hides dialect-specific behavior (UUID columns, case-sensitivity, unique
 * constraint error codes). Testing against the same engine we run in
 * production catches the bugs that only surface there. Alpine keeps the image
 * small enough for a CI runner.</p>
 *
 * <p><b>Why import {@link TestContainersConfig} (and not a static
 * {@code @Container} field)?</b>
 * The container's lifecycle must align with the {@code ApplicationContext}, so
 * Spring's TestContext cache can reuse a single context (and a single running
 * container) across test classes. A JUnit-managed static field is torn down
 * per class, which races with context caching and surfaces as
 * {@code Connection refused} after the JPA timeout. See
 * {@link TestContainersConfig} for the full rationale.</p>
 *
 * <p><b>What about {@code @DataJpaTest} slice tests?</b>
 * They live in their own classes (e.g. {@code UserRepositoryImplIT}) because
 * the slice does not boot the web layer or security. This base is for the
 * full-stack tests that need all of it; they must also
 * {@code @Import(TestContainersConfig.class)} explicitly since they don't
 * extend this class.</p>
 *
 * <p><b>Annotation inheritance note:</b>
 * {@code @SpringBootTest} and {@code @AutoConfigureMockMvc} on this abstract
 * class are honored by subclasses without re-declaration. A subclass may still
 * override {@code @SpringBootTest} (e.g. to activate a different profile) —
 * that creates a separate cached context, which is the desired behavior.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestContainersConfig.class)
public abstract class AbstractIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected RateLimiter rateLimiter;

    /**
     * Resets the in-memory rate limiter between tests.
     *
     * <p>Every {@code @SpringBootTest} class shares one ApplicationContext, so
     * the per-IP bucket would otherwise accumulate across test methods and trip
     * the limit spuriously (a single class can easily issue 10+ auth calls).
     * Tests that <em>rely</em> on accumulation do so within a single method
     * starting from this clean slate.</p>
     */
    @BeforeEach
    void resetRateLimiter() {
        rateLimiter.reset();
    }
}
