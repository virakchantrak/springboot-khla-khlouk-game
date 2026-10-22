package kh.virakchantrak.KhlaKhlouk.auth.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kh.virakchantrak.KhlaKhlouk.auth.audit.SecurityAudit;
import kh.virakchantrak.KhlaKhlouk.auth.service.JwtService;
import kh.virakchantrak.KhlaKhlouk.auth.service.PlayerDetailsService;
import kh.virakchantrak.KhlaKhlouk.common.response.ApiErrorWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final PlayerDetailsService playerDetailsService;

    // Login and registration must work even if the client still holds an old token.
    // Everything else (including /api/v1/auth/password) is still checked.
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();

        return "POST".equals(method)
                && ("/api/v1/auth/login".equals(path)
                || "/api/v1/players".equals(path));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            authenticate(authHeader.substring(7));
        } catch (JwtException | IllegalArgumentException | UsernameNotFoundException e) {
            SecurityContextHolder.clearContext();

            SecurityAudit.invalidToken(
                    request.getRemoteAddr(),
                    request.getMethod(),
                    request.getRequestURI()
            );

            ApiErrorWriter.write(
                    response,
                    HttpStatus.UNAUTHORIZED,
                    "INVALID_TOKEN",
                    "Invalid or expired token"
            );
            return;
        }

        // outside the try: only token checking is covered by the catch above
        filterChain.doFilter(request, response);
    }

    private void authenticate(String token) {
        String username = jwtService.extractUsername(token);

        if (username != null
                && SecurityContextHolder.getContext().getAuthentication() == null) {

            UserDetails userDetails =
                    playerDetailsService.loadUserByUsername(username);

            if (jwtService.isTokenValid(token, userDetails)) {
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                userDetails.getAuthorities()
                        );

                SecurityContextHolder.getContext()
                        .setAuthentication(authentication);
            }
        }
    }
}