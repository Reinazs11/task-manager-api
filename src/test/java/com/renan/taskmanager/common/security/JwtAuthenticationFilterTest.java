package com.renan.taskmanager.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.impl.DefaultClaims;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link JwtAuthenticationFilter}.
 *
 * <p>The filter has five decision branches, each covered here:</p>
 * <ol>
 *   <li>No Authorization header → unauthenticated, chain continues.</li>
 *   <li>Header present but not "Bearer ..." → unauthenticated, chain continues.</li>
 *   <li>Valid access token → authentication populated in SecurityContext.</li>
 *   <li>Refresh token used as access → JwtException from parseAccessToken, unauthenticated.</li>
 *   <li>Any other JwtException (expired, tampered) → unauthenticated.</li>
 * </ol>
 *
 * <p><b>Why mock {@link JwtService}?</b>
 * The filter's contract is "what to do with each parse outcome", not how the
 * signature/exp validation works — that is covered exhaustively in
 * {@code JwtServiceTest} and {@code JwtAuthorizationIT}. Mocking lets us drive
 * each branch deterministically without constructing real tokens.</p>
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private FilterChain chain;

    @InjectMocks
    private JwtAuthenticationFilter filter;

    @AfterEach
    void clearSecurityContext() {
        // Each test should start and end with a clean context to avoid leakage.
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("No Authorization header → no auth, chain continues")
    void shouldSkipWhenNoAuthorizationHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(jwtService, never()).parseAccessToken(any());
        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("Authorization header without Bearer prefix → no auth, chain continues")
    void shouldSkipWhenHeaderIsNotBearer() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Basic abc");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(jwtService, never()).parseAccessToken(any());
        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("Valid access token → SecurityContext populated with subject and ROLE_USER")
    void shouldPopulateAuthContextOnValidAccessToken() throws Exception {
        String token = "valid.access.token";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        Claims claims = new DefaultClaims(Map.of("sub", "user-123"));
        when(jwtService.parseAccessToken(token)).thenReturn(claims);

        filter.doFilterInternal(request, response, chain);

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isEqualTo("user-123");
        assertThat(auth.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_USER");
        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("Refresh token used as access → parseAccessToken throws, unauthenticated")
    void shouldStayUnauthenticatedWhenRefreshTokenUsedAsAccess() throws Exception {
        String token = "refresh.used.as.access";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtService.parseAccessToken(token))
                .thenThrow(new JwtException("Token type mismatch (expected access, got refresh)"));

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("Expired/tampered token (any JwtException) → unauthenticated, chain continues")
    void shouldStayUnauthenticatedOnGenericJwtException() throws Exception {
        String token = "expired.or.tampered";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtService.parseAccessToken(token)).thenThrow(new JwtException("invalid"));

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }
}
