package com.renan.taskmanager.common.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ClientIpResolver}.
 *
 * <p>The resolver picks the client IP that rate-limiting keys on. The contract
 * depends on the {@code trustForwardedFor} flag:</p>
 * <ul>
 *   <li><b>trusted (opt-in)</b> — prefer the first hop of
 *       {@code X-Forwarded-For} (set by reverse proxies); fall back to the raw
 *       socket {@code remoteAddr} when the header is missing or blank.</li>
 *   <li><b>untrusted (default)</b> — always use {@code remoteAddr}, so a client
 *       cannot spoof XFF to dodge the limit when there is no proxy in front.</li>
 * </ul>
 */
class ClientIpResolverTest {

    @Nested
    @DisplayName("With trustForwardedFor = true")
    class TrustedForwarded {

        private final ClientIpResolver resolver = new ClientIpResolver(true);

        @Test
        @DisplayName("Should return the single IP from X-Forwarded-For")
        void shouldReturnSingleForwardedIp() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("X-Forwarded-For", "203.0.113.5");

            assertThat(resolver.resolve(request)).isEqualTo("203.0.113.5");
        }

        @Test
        @DisplayName("Should return the first IP when X-Forwarded-For has multiple hops")
        void shouldReturnFirstHopWhenMultipleForwarded() {
            // Proxies chain client IPs left-to-right: client, proxy1, proxy2.
            // The first entry is the originating client — that's the one we throttle.
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("X-Forwarded-For", "203.0.113.5, 10.0.0.1, 10.0.0.2");
            request.setRemoteAddr("10.0.0.99");

            assertThat(resolver.resolve(request)).isEqualTo("203.0.113.5");
        }

        @Test
        @DisplayName("Should trim whitespace around the first forwarded IP")
        void shouldTrimForwardedIp() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("X-Forwarded-For", "  203.0.113.5  , 10.0.0.1");

            assertThat(resolver.resolve(request)).isEqualTo("203.0.113.5");
        }

        @Test
        @DisplayName("Should fall back to remoteAddr when X-Forwarded-For is empty")
        void shouldFallBackWhenHeaderEmpty() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("X-Forwarded-For", "");
            request.setRemoteAddr("192.168.0.10");

            assertThat(resolver.resolve(request)).isEqualTo("192.168.0.10");
        }

        @Test
        @DisplayName("Should fall back to remoteAddr when X-Forwarded-For is missing")
        void shouldFallBackWhenHeaderMissing() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr("192.168.0.10");

            assertThat(resolver.resolve(request)).isEqualTo("192.168.0.10");
        }

        @Test
        @DisplayName("Should fall back to remoteAddr when X-Forwarded-For is only whitespace")
        void shouldFallBackWhenHeaderIsWhitespace() {
            // Defensive: a misconfigured proxy could send a blank-ish header.
            // Treat it as absent rather than keying every request on " ".
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("X-Forwarded-For", "   ");
            request.setRemoteAddr("192.168.0.10");

            assertThat(resolver.resolve(request)).isEqualTo("192.168.0.10");
        }
    }

    @Nested
    @DisplayName("With trustForwardedFor = false (default)")
    class UntrustedForwarded {

        private final ClientIpResolver resolver = new ClientIpResolver(false);

        @Test
        @DisplayName("Should ignore X-Forwarded-For and use remoteAddr")
        void shouldIgnoreForwardedHeader() {
            // Without a trusted proxy in front, XFF is client-controlled and must
            // not be honored — otherwise an attacker rotates the header value to
            // bypass the per-IP limit.
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("X-Forwarded-For", "203.0.113.5");
            request.setRemoteAddr("192.168.0.10");

            assertThat(resolver.resolve(request)).isEqualTo("192.168.0.10");
        }

        @Test
        @DisplayName("Should use remoteAddr when no X-Forwarded-For is present")
        void shouldUseRemoteAddr() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr("192.168.0.10");

            assertThat(resolver.resolve(request)).isEqualTo("192.168.0.10");
        }
    }
}
