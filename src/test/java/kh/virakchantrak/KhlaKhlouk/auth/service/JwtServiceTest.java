package kh.virakchantrak.KhlaKhlouk.auth.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import kh.virakchantrak.KhlaKhlouk.auth.config.JwtProperties;
import kh.virakchantrak.KhlaKhlouk.auth.security.PlayerPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final String SECRET = "BwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwc=";

    private final JwtService jwtService =
            new JwtService(new JwtProperties(SECRET, 3_600_000));

    private PlayerPrincipal player(String username, int tokenVersion) {
        return new PlayerPrincipal(
                UUID.randomUUID(), username, "hash",
                List.of(new SimpleGrantedAuthority("ROLE_PLAYER")),
                tokenVersion);
    }

    @Test
    void tokenIsValidForSameUserAndSameVersion() {
        String token = jwtService.generateToken(player("alice", 0));

        assertThat(jwtService.extractUsername(token)).isEqualTo("alice");
        assertThat(jwtService.isTokenValid(token, player("alice", 0))).isTrue();
    }

    @Test
    void tokenStopsBeingValidWhenThePlayersVersionMovesOn() {
        String token = jwtService.generateToken(player("alice", 0));

        assertThat(jwtService.isTokenValid(token, player("alice", 1))).isFalse();
    }

    @Test
    void newTokenCarriesTheNewVersion() {
        String token = jwtService.generateToken(player("alice", 3));

        assertThat(jwtService.isTokenValid(token, player("alice", 3))).isTrue();
        assertThat(jwtService.isTokenValid(token, player("alice", 2))).isFalse();
    }

    @Test
    void tokenOfAnotherUserIsInvalid() {
        String token = jwtService.generateToken(player("alice", 0));

        assertThat(jwtService.isTokenValid(token, player("bob", 0))).isFalse();
    }

    @Test
    void oldTokensWithoutVersionClaimCountAsVersionZero() {
        // a token exactly as the previous version of the app issued it
        String oldToken = Jwts.builder()
                .subject("alice")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET)))
                .compact();

        assertThat(jwtService.isTokenValid(oldToken, player("alice", 0))).isTrue();
        assertThat(jwtService.isTokenValid(oldToken, player("alice", 1))).isFalse();
    }
}