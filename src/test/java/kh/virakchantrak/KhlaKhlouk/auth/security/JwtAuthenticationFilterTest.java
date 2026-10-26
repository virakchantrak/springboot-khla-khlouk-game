package kh.virakchantrak.KhlaKhlouk.auth.security;

import io.jsonwebtoken.JwtException;
import kh.virakchantrak.KhlaKhlouk.auth.service.JwtService;
import kh.virakchantrak.KhlaKhlouk.auth.service.PlayerDetailsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock JwtService jwtService;
    @Mock PlayerDetailsService playerDetailsService;

    private JwtAuthenticationFilter filter() {
        return new JwtAuthenticationFilter(jwtService, playerDetailsService);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest request(String method, String uri, String authorization) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        if (authorization != null) {
            request.addHeader("Authorization", authorization);
        }
        return request;
    }

    // ---------- public endpoints must ignore stale tokens ----------

    @Test
    void staleTokenDoesNotBlockLogin() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter().doFilter(
                request("POST", "/api/v1/auth/login", "Bearer stale"), response, chain);

        assertThat(chain.getRequest()).isNotNull();        // the request went through
        assertThat(response.getStatus()).isEqualTo(200);
        verifyNoInteractions(jwtService);
    }

    @Test
    void staleTokenDoesNotBlockRegistration() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter().doFilter(
                request("POST", "/api/v1/players", "Bearer stale"), response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
        verifyNoInteractions(jwtService);
    }

    // ---------- protected endpoints must still reject bad tokens ----------

    @Test
    void staleTokenIsRejectedOnProtectedEndpoint() throws Exception {
        when(jwtService.extractUsername("stale")).thenThrow(new JwtException("expired"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter().doFilter(
                request("GET", "/api/v1/players/me", "Bearer stale"), response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("Invalid or expired token");
        assertThat(response.getContentAsString()).contains("\"code\":\"INVALID_TOKEN\"");
        assertThat(chain.getRequest()).isNull();           // the request was stopped
    }

    @Test
    void staleTokenIsRejectedOnPasswordChange() throws Exception {
        when(jwtService.extractUsername("stale")).thenThrow(new JwtException("expired"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter().doFilter(
                request("PUT", "/api/v1/auth/password", "Bearer stale"), response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void tokenOfAPlayerThatNoLongerExistsReturns401() throws Exception {
        when(jwtService.extractUsername("orphan")).thenReturn("ghost");
        when(playerDetailsService.loadUserByUsername("ghost"))
                .thenThrow(new UsernameNotFoundException("Player not found"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter().doFilter(
                request("GET", "/api/v1/players/me", "Bearer orphan"), response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
    }

    // ---------- normal behaviour ----------

    @Test
    void requestWithoutTokenPassesThroughUnauthenticated() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter().doFilter(request("GET", "/api/v1/players/me", null), response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(jwtService);
    }

    @Test
    void validTokenAuthenticatesThePlayer() throws Exception {
        PlayerPrincipal player = new PlayerPrincipal(
                UUID.randomUUID(), "alice", "hash",
                List.of(new SimpleGrantedAuthority("ROLE_PLAYER")));

        when(jwtService.extractUsername("good")).thenReturn("alice");
        when(playerDetailsService.loadUserByUsername("alice")).thenReturn(player);
        when(jwtService.isTokenValid("good", player)).thenReturn(true);

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter().doFilter(
                request("GET", "/api/v1/players/me", "Bearer good"), response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName())
                .isEqualTo("alice");
    }
}