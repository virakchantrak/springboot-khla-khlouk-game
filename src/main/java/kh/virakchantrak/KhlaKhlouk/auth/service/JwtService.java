package kh.virakchantrak.KhlaKhlouk.auth.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import kh.virakchantrak.KhlaKhlouk.auth.config.JwtProperties;
import kh.virakchantrak.KhlaKhlouk.auth.security.PlayerPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;

@Service
@RequiredArgsConstructor
public class JwtService {

    private static final String TOKEN_VERSION_CLAIM = "ver";

    private final JwtProperties jwtProperties;

    public String generateToken(UserDetails userDetails) {
        Instant now = Instant.now();

        return Jwts.builder()
                .subject(userDetails.getUsername())
                .claim(TOKEN_VERSION_CLAIM, tokenVersionOf(userDetails))
                .issuedAt(Date.from(now))
                .expiration(Date.from(
                        now.plusMillis(jwtProperties.expiration())
                ))
                .signWith(getSigningKey())
                .compact();
    }

    public String extractUsername(String token) {
        return extractAllClaims(token).getSubject();
    }

    public boolean isTokenValid(
            String token,
            UserDetails userDetails
    ) {
        Claims claims = extractAllClaims(token);

        return userDetails.getUsername().equals(claims.getSubject())
                && !claims.getExpiration().before(new Date())
                && tokenVersionOf(claims) == tokenVersionOf(userDetails);
    }

    // tokens issued before this feature have no version claim: treat as 0
    private int tokenVersionOf(Claims claims) {
        Integer version = claims.get(TOKEN_VERSION_CLAIM, Integer.class);
        return version == null ? 0 : version;
    }

    private int tokenVersionOf(UserDetails userDetails) {
        return userDetails instanceof PlayerPrincipal principal
                ? principal.getTokenVersion()
                : 0;
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(
                Decoders.BASE64.decode(jwtProperties.secret())
        );
    }
}