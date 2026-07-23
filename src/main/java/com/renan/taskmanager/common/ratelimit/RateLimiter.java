package com.renan.taskmanager.common.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.TimeMeter;

import java.time.Duration;
import java.util.function.Function;

/**
 * In-memory, per-key token-bucket rate limiter backed by Bucket4j, with a
 * bounded Caffeine cache as the bucket store.
 *
 * <p>Each distinct key (the client IP, resolved by {@link ClientIpResolver}) gets
 * its own {@link Bucket}. All buckets share the same capacity/refill configured
 * at construction. State lives in-process: it is not shared across instances
 * and resets on restart (see {@code DECISIONS.md} #15).</p>
 *
 * <p><b>Why Caffeine and not a plain {@code ConcurrentHashMap}?</b>
 * A naive map grows without bound. Since the key is the client IP and
 * {@code X-Forwarded-For} can be spoofed, an attacker could flood distinct IPs
 * and exhaust the heap — turning the brute-force defense into a cheap DoS
 * vector. Caffeine caps the map size ({@code maximumSize}) and evicts idle
 * buckets ({@code expireAfterAccess}), bounding memory regardless of key
 * diversity.</p>
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

    private final Cache<String, Bucket> buckets;
    private final Function<String, Bucket> bucketFactory;

    /**
     * Constructs a limiter with the given bucket configuration and a system-clock
     * bucket store. Production path.
     *
     * @param capacity          maximum tokens a bucket can hold (burst tolerance)
     * @param refillTokens      tokens added back per {@code refillPeriod}
     * @param refillPeriod      window over which {@code refillTokens} are restored
     * @param maxBuckets        hard cap on tracked IPs (heap bound)
     * @param expireAfterAccess how long an idle bucket stays before eviction
     */
    public RateLimiter(int capacity, int refillTokens, Duration refillPeriod,
                       long maxBuckets, Duration expireAfterAccess) {
        this(capacity, refillTokens, refillPeriod, maxBuckets, expireAfterAccess, TimeMeter.SYSTEM_MILLISECONDS);
    }

    /**
     * Test-friendly constructor: accepts a {@link TimeMeter} so tests can drive
     * the clock deterministically instead of sleeping real time (no CI flakiness).
     */
    RateLimiter(int capacity, int refillTokens, Duration refillPeriod,
                long maxBuckets, Duration expireAfterAccess, TimeMeter timeMeter) {
        this.buckets = Caffeine.newBuilder()
                .maximumSize(maxBuckets)
                .expireAfterAccess(expireAfterAccess)
                .build();
        this.bucketFactory = key -> Bucket.builder()
                .addLimit(limit -> limit.capacity(capacity).refillGreedy(refillTokens, refillPeriod))
                .withCustomTimePrecision(timeMeter)
                .build();
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
        Bucket bucket = buckets.get(key, bucketFactory);
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            return new Result(true, 0L);
        }
        return new Result(false, retryAfterSeconds(probe));
    }

    /**
     * Drops all per-key bucket state.
     *
     * <p>Intended for test isolation: a {@code @SpringBootTest} class caches one
     * {@link RateLimiter} shared across its test methods, so without a reset the
     * per-IP bucket would accumulate and trip the limit spuriously mid-class.
     * Each test class gets its own ApplicationContext, so state never leaks
     * across classes. Outside tests the in-memory state is ephemeral anyway
     * (lost on restart).</p>
     */
    public void reset() {
        buckets.invalidateAll();
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
