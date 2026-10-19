package kh.virakchantrak.KhlaKhlouk.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CorsConfigTest {

    @Test
    void acceptsNormalOrigins() {
        List<String> origins = List.of(
                "http://localhost:3000",
                "https://game.example.com",
                "http://192.168.1.20:5173");

        assertThat(CorsConfig.validate(origins)).isEqualTo(origins);
    }

    @Test
    void rejectsWildcard() {
        assertThatThrownBy(() -> CorsConfig.validate(List.of("*")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CorsConfig.validate(List.of("https://*.example.com")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsTrailingSlashAndPaths() {
        assertThatThrownBy(() -> CorsConfig.validate(List.of("http://localhost:3000/")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("http://localhost:3000/");
        assertThatThrownBy(() -> CorsConfig.validate(List.of("https://example.com/app")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMissingSchemeOrBlankEntries() {
        assertThatThrownBy(() -> CorsConfig.validate(List.of("localhost:3000")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CorsConfig.validate(List.of("")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CorsConfig.validate(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}