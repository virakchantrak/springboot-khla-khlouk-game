package kh.virakchantrak.KhlaKhlouk.auth.config;

import io.jsonwebtoken.io.Decoders;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        String secret,
        long expiration
) {

    // HS256 needs a key of at least 256 bits = 32 bytes
    private static final int MIN_SECRET_BYTES = 32;

    public JwtProperties {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException(
                    "jwt.secret must be set (use the JWT_SECRET environment variable)");
        }

        byte[] keyBytes;
        try {
            // the same decoder JwtService uses, so validation and usage can never disagree
            keyBytes = Decoders.BASE64.decode(secret);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("jwt.secret must be a valid Base64 string");
        }

        if (keyBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalArgumentException(
                    "jwt.secret must decode to at least " + MIN_SECRET_BYTES
                            + " bytes (256 bits)");
        }

        if (expiration <= 0) {
            throw new IllegalArgumentException(
                    "jwt.expiration must be greater than zero");
        }
    }
}