package com.renan.taskmanager.common.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link RateLimiter}.
 *
 * <p>The limiter is a thin wrapper over a Bucket4j token bucket keyed by an
 * arbitrary string (the client IP, resolved elsewhere). It must:</p>
 * <ul>
 *   <li>admit exactly {@code capacity} requests in a burst, then deny;</li>
 *   <li>keep per-key state independent (one client cannot exhaust another's bucket);</li>
 *   <li>report a positive {@code retryAfterSeconds} on denial (drives the
 *       {@code Retry-After} header) and zero on admission.</li>
 * </ul>
 */
class RateLimiterTest {

    private static final int CAPACITY = 5;
    private static final int REFILL_TOKENS = 5;
    private static final Duration REFILL_PERIOD = Duration.ofSeconds(60);

    private final RateLimiter limiter = new RateLimiter(CAPACITY, REFILL_TOKENS, REFILL_PERIOD);

    @Test
    @DisplayName("Should admit requests up to capacity")
    void shouldAdmitUpToCapacity() {
        for (int i = 0; i < CAPACITY; i++) {
            RateLimiter.Result result = limiter.tryConsume("1.2.3.4");
            assertThat(result.allowed())
                    .as("request #%d within capacity should be admitted", i + 1)
                    .isTrue();
            assertThat(result.retryAfterSeconds())
                    .as("no retry needed while tokens remain")
                    .isZero();
        }
    }

    @Test
    @DisplayName("Should deny the request immediately after capacity is reached")
    void shouldDenyAfterCapacityReached() {
        for (int i = 0; i < CAPACITY; i++) {
            limiter.tryConsume("1.2.3.4");
        }

        RateLimiter.Result overLimit = limiter.tryConsume("1.2.3.4");

        assertThat(overLimit.allowed()).isFalse();
        assertThat(overLimit.retryAfterSeconds())
                .as("denial must report a positive wait until the next token")
                .isPositive();
    }

    @Test
    @DisplayName("Should keep buckets independent per key")
    void shouldKeepBucketsIndependentPerKey() {
        // Exhaust client A's bucket entirely.
        for (int i = 0; i < CAPACITY; i++) {
            limiter.tryConsume("1.1.1.1");
        }
        assertThat(limiter.tryConsume("1.1.1.1").allowed()).isFalse();

        // Client B must be unaffected.
        RateLimiter.Result clientB = limiter.tryConsume("2.2.2.2");
        assertThat(clientB.allowed()).isTrue();
    }

    @Test
    @DisplayName("Should admit again after a full refill period elapses")
    void shouldAdmitAgainAfterRefill() throws InterruptedException {
        // Use a short refill window so the test stays fast but still proves the
        // bucket actually refills over real time (not just on paper).
        RateLimiter fastLimiter = new RateLimiter(2, 2, Duration.ofMillis(200));
        fastLimiter.tryConsume("k");
        fastLimiter.tryConsume("k");
        assertThat(fastLimiter.tryConsume("k").allowed()).isFalse();

        Thread.sleep(250);

        assertThat(fastLimiter.tryConsume("k").allowed())
                .as("bucket must have refilled after the period")
                .isTrue();
    }
}
