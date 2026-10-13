package kh.virakchantrak.KhlaKhlouk.auth.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtPropertiesTest {

    // Base64 strings of exactly 32, 31 and 16 bytes
    private static final String BYTES_32 = "BwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwc=";
    private static final String BYTES_31 = "BwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBw==";
    private static final String BYTES_16 = "BwcHBwcHBwcHBwcHBwcHBw==";

    @Test
    void acceptsSecretOfExactlyMinimumLength() {
        JwtProperties properties = new JwtProperties(BYTES_32, 3_600_000);

        assertThat(properties.secret()).isEqualTo(BYTES_32);
        assertThat(properties.expiration()).isEqualTo(3_600_000);
    }

    @Test
    void rejectsMissingSecret() {
        assertThatThrownBy(() -> new JwtProperties(null, 3_600_000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jwt.secret must be set");
    }

    @Test
    void rejectsBlankSecret() {
        assertThatThrownBy(() -> new JwtProperties("   ", 3_600_000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jwt.secret must be set");
    }

    @Test
    void rejectsSecretThatIsNotBase64() {
        assertThatThrownBy(() -> new JwtProperties("this is not base64!!", 3_600_000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Base64");
    }

    @Test
    void rejectsSecretOneByteTooShort() {
        assertThatThrownBy(() -> new JwtProperties(BYTES_31, 3_600_000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 32 bytes");
    }

    @Test
    void rejectsVeryShortSecret() {
        assertThatThrownBy(() -> new JwtProperties(BYTES_16, 3_600_000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 32 bytes");
    }

    @Test
    void rejectsZeroOrNegativeExpiration() {
        assertThatThrownBy(() -> new JwtProperties(BYTES_32, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jwt.expiration");

        assertThatThrownBy(() -> new JwtProperties(BYTES_32, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jwt.expiration");
    }
}