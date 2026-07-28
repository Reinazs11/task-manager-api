package com.renan.taskmanager.common.api;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Exposes a single {@link Clock} bean for the application layer.
 *
 * <p><b>Why a bean and not {@code Instant.now()} inline?</b>
 * Use cases that record timestamps (e.g. the {@code revokedAt} on refresh-token
 * rotation) depend on {@link Clock#instant()}. Injecting the clock keeps the
 * use case deterministic in tests: a fixed clock makes the recorded timestamp
 * assertable instead of a moving target. Production uses the system UTC clock.</p>
 *
 * <p><b>Why UTC?</b> The domain uses {@link java.time.Instant} exclusively,
 * which is already on the UTC timeline — the zone here only affects
 * zone-aware conversions at the edges, and UTC is the safest default.</p>
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
