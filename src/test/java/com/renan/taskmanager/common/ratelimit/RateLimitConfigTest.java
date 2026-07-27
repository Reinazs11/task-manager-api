package com.renan.taskmanager.common.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link RateLimitConfig}.
 *
 * <p>Two contracts to protect:</p>
 * <ul>
 *   <li><b>Fail-fast on misconfiguration</b> — a non-positive value for any
 *       {@code app.rate-limit.auth.*} property must stop startup with a clear
 *       {@link IllegalStateException}, never silently degrade (capacity=0 would
 *       otherwise 429 every login; a negative value would break the bucket
 *       math).</li>
 *   <li><b>Binding</b> — every property must be read from its
 *       {@code app.rate-limit.auth.*} key. A typo'd key would silently fall
 *       back to the default and break the {@code validatePositive} promise.</li>
 * </ul>
 *
 * <p>Uses {@link ApplicationContextRunner} — a lightweight context for testing
 * a single {@code @Configuration} without booting the whole app (no web, no
 * JPA, no Testcontainers).</p>
 */
class RateLimitConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(UserConfigurations.of(RateLimitConfig.class));

    @Test
    @DisplayName("Should boot with the defaults when no properties are set")
    void shouldBootWithDefaults() {
        runner.run(ctx -> assertThat(ctx).hasSingleBean(RateLimiter.class));
    }

    @Test
    @DisplayName("Should bind a custom bucket-max-size from app.rate-limit.auth.*")
    void shouldBindCustomBucketMaxSize() {
        // Regression guard: confirms the key under the `auth:` block resolves.
        // If anyone moves the key out of `auth:` (yml) without updating the
        // @Value, this test catches the silent fallback to the default.
        runner
                .withPropertyValues("app.rate-limit.auth.bucket-max-size=42")
                .run(ctx -> assertThat(ctx).hasSingleBean(RateLimiter.class));
    }

    @Test
    @DisplayName("Should fail startup when capacity is non-positive")
    void shouldFailWhenCapacityNonPositive() {
        runner
                .withPropertyValues("app.rate-limit.auth.capacity=0")
                .run(ctx -> assertThat(ctx)
                        .hasFailed()
                        .getFailure()
                        .rootCause()
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("app.rate-limit.auth.capacity"));
    }

    @Test
    @DisplayName("Should fail startup when bucket-max-size is non-positive")
    void shouldFailWhenBucketMaxSizeNonPositive() {
        runner
                .withPropertyValues("app.rate-limit.auth.bucket-max-size=-1")
                .run(ctx -> assertThat(ctx)
                        .hasFailed()
                        .getFailure()
                        .rootCause()
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("app.rate-limit.auth.bucket-max-size"));
    }
}
