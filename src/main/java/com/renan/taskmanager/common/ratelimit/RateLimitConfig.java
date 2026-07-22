package com.renan.taskmanager.common.ratelimit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Wires the {@link RateLimiter} as a singleton bean from the
 * {@code app.rate-limit.auth.*} properties.
 *
 * <p><b>Why a bean (and not {@code new RateLimiter(...)} inside the filter)?</b>
 * Lifting the limiter out of the filter lets integration tests obtain the same
 * instance and {@link RateLimiter#reset()} it between tests, so the per-IP
 * bucket never leaks across test methods within a class. The filter stays
 * focused on HTTP glue; the policy and its state live in the {@link RateLimiter}.</p>
 */
@Configuration
public class RateLimitConfig {

    /**
     * @param capacity               max tokens per IP bucket (burst tolerance)
     * @param refillTokens           tokens restored per refill window
     * @param refillPeriodSeconds    length of the refill window, in seconds
     * @param maxBuckets             hard cap on tracked IPs (heap bound against XFF spoof)
     * @param expireAfterAccessMin   idle TTL before a bucket is evicted
     */
    @Bean
    @ConditionalOnMissingBean
    public RateLimiter authRateLimiter(@Value("${app.rate-limit.auth.capacity:10}") int capacity,
                                       @Value("${app.rate-limit.auth.refill-tokens:10}") int refillTokens,
                                       @Value("${app.rate-limit.auth.refill-period-seconds:60}") long refillPeriodSeconds,
                                       @Value("${app.rate-limit.auth.bucket-max-size:100000}") long maxBuckets,
                                       @Value("${app.rate-limit.auth.bucket-expire-after-access-minutes:60}") long expireAfterAccessMin) {
        return new RateLimiter(
                capacity,
                refillTokens,
                Duration.ofSeconds(refillPeriodSeconds),
                maxBuckets,
                Duration.ofMinutes(expireAfterAccessMin)
        );
    }
}
