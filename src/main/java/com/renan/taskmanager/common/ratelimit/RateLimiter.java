package com.renan.taskmanager.common.ratelimit;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * In-memory, per-key token-bucket rate limiter backed by Bucket4j.
 *
 * <p>Each distinct key (the client IP, resolved by {@link ClientIpResolver}) gets
 * its own {@link Bucket}. All buckets share the same capacity/refill configured
 * at construction. State lives in-process: it is not shared across instances
 * and resets on restart (see {@code DECISIONS.md}).</p>
 *
 * <p><b>Why token bucket?</b> A naive "N requests per minute" counter either
 * rejects legitimate short bursts or refills in coarse steps. Token bucket
 * tolerates a burst up to {@code capacity} and refills continuously at a steady
 * rate, so the sustained average is bounded without punishing normal usage.</p>
 */
public final class RateLimiter {

    /**
     * Outcome of a rate-limit check.
     *
     * @param allowed           whether the request consumed a token and may proceed
     * @param retryAfterSeconds seconds the caller should wait before retrying;
     *                          zero when {@code allowed} is {@code true}
     */
    public record Result(boolean allowed, long retryAfterSeconds) {}

    private final ConcurrentMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final int capacity;
    private final int refillTokens;
    private final Duration refillPeriod;

    /**
     * Constructs a limiter with the given bucket configuration, applied to every key.
     *
     * @param capacity     maximum tokens a bucket can hold (burst tolerance)
     * @param refillTokens tokens added back per {@code refillPeriod}
     * @param refillPeriod window over which {@code refillTokens} are restored
     */
    public RateLimiter(int capacity, int refillTokens, Duration refillPeriod) {
        this.capacity = capacity;
        this.refillTokens = refillTokens;
        this.refillPeriod = refillPeriod;
    }

    /**
     * Attempts to consume one token for the given key.
     *
     * <p>Looks up (or lazily creates) the bucket for {@code key} and probes a
     * single-token consumption. The probe also reports how long until the next
     * token would be available, which feeds the {@code Retry-After} header on
     * denial.</p>
     *
     * @param key the rate-limit key (typically the client IP)
     * @return the result of the consumption probe
     */
    public Result tryConsume(String key) {
        Bucket bucket = buckets.computeIfAbsent(key, k -> newBucket());
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            return new Result(true, 0L);
        }
        return new Result(false, retryAfterSeconds(probe));
    }

    /**
     * Drops all per-key bucket state.
     *
     * <p>Intended for test isolation: integration tests share a single
     * ApplicationContext (and therefore a single {@link RateLimiter}) across
     * methods, so without a reset the per-IP bucket would accumulate across
     * tests and trip the limit spuriously. Outside tests the in-memory state is
     * ephemeral anyway (lost on restart).</p>
     */
    public void reset() {
        buckets.clear();
    }

    private Bucket newBucket() {
        return Bucket.builder()
                .addLimit(limit -> limit.capacity(capacity).refillGreedy(refillTokens, refillPeriod))
                .build();
    }

    /**
     * Converts the probe's nanos-to-refill to whole seconds, floored at 1 so the
     * {@code Retry-After} header never tells a throttled client to retry in 0s.
     */
    private static long retryAfterSeconds(ConsumptionProbe probe) {
        long seconds = probe.getNanosToWaitForRefill() / 1_000_000_000L;
        return Math.max(1L, seconds);
    }
}
