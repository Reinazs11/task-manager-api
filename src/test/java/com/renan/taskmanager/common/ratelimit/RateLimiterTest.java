package com.renan.taskmanager.common.ratelimit;

import io.github.bucket4j.TimeMeter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

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
 *       {@code Retry-After} header) and zero on admission;</li>
 *   <li>refill the bucket after the configured period elapses.</li>
 * </ul>
 *
 * <p><b>Deterministic clock.</b> Tests inject a fake {@link TimeMeter} whose
 * nanos advance on demand, so the refill behavior is verified without
 * {@code Thread.sleep} (which is flaky on slow/loaded CI runners).</p>
 */
class RateLimiterTest {

    private static final int CAPACITY = 5;
    private static final int REFILL_TOKENS = 5;
    private static final Duration REFILL_PERIOD = Duration.ofSeconds(60);
    private static final long MAX_BUCKETS = 1000L;
    private static final Duration EXPIRE = Duration.ofMinutes(60);

    /**
     * Controllable clock: {@link #advance(Duration)} moves the bucket's view of
     * "now" forward so refill math resolves deterministically.
     */
    private static final class FakeClock implements TimeMeter {
        private final AtomicLong nanos = new AtomicLong();

        @Override
        public long currentTimeNanos() {
            return nanos.get();
        }

        @Override
        public boolean isWallClockBased() {
            return false;
        }

        void advance(Duration d) {
            nanos.addAndGet(d.toNanos());
        }
    }

    private RateLimiter newLimiter(FakeClock clock) {
        return new RateLimiter(CAPACITY, REFILL_TOKENS, REFILL_PERIOD, MAX_BUCKETS, EXPIRE, clock);
    }

    @Test
    @DisplayName("Should admit requests up to capacity")
    void shouldAdmitUpToCapacity() {
        RateLimiter limiter = newLimiter(new FakeClock());

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
        RateLimiter limiter = newLimiter(new FakeClock());
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
        RateLimiter limiter = newLimiter(new FakeClock());

        // Exhaust client A's bucket entirely.
        for (int i = 0; i < CAPACITY; i++) {
            limiter.tryConsume("1.1.1.1");
        }
        assertThat(limiter.tryConsume("1.1.1.1").allowed()).isFalse();

        // Client B must be unaffected.
        assertThat(limiter.tryConsume("2.2.2.2").allowed()).isTrue();
    }

    @Test
    @DisplayName("Should admit again after a full refill period elapses")
    void shouldAdmitAgainAfterRefill() {
        FakeClock clock = new FakeClock();
        RateLimiter limiter = new RateLimiter(2, 2, Duration.ofSeconds(1), MAX_BUCKETS, EXPIRE, clock);

        limiter.tryConsume("k");
        limiter.tryConsume("k");
        assertThat(limiter.tryConsume("k").allowed()).isFalse();

        // Advance the bucket's clock past one refill window — no real sleep.
        clock.advance(Duration.ofSeconds(1));

        assertThat(limiter.tryConsume("k").allowed())
                .as("bucket must have refilled after the period")
                .isTrue();
    }

    @Test
    @DisplayName("reset() should drop all per-key state")
    void resetShouldClearState() {
        RateLimiter limiter = newLimiter(new FakeClock());
        for (int i = 0; i < CAPACITY; i++) {
            limiter.tryConsume("9.9.9.9");
        }
        assertThat(limiter.tryConsume("9.9.9.9").allowed()).isFalse();

        limiter.reset();

        // After reset the bucket is recreated full.
        assertThat(limiter.tryConsume("9.9.9.9").allowed())
                .as("reset() must give the key a fresh bucket")
                .isTrue();
    }
}
