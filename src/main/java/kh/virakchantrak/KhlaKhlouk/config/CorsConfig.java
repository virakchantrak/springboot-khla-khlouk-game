package kh.virakchantrak.KhlaKhlouk.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;

@Configuration
public class CorsConfig {

    // scheme + host (+ port), nothing else: no path, no trailing slash, no wildcard
    private static final Pattern ORIGIN = Pattern.compile("^https?://[^/*\\s]+$");

    static List<String> validate(List<String> origins) {
        if (origins == null || origins.isEmpty()) {
            throw new IllegalArgumentException(
                    "app.allowed-origins must list at least one origin");
        }

        for (String origin : origins) {
            if (origin == null || !ORIGIN.matcher(origin).matches()) {
                throw new IllegalArgumentException(
                        "Invalid origin in app.allowed-origins: '" + origin
                                + "' (use exactly scheme://host[:port], e.g. http://localhost:3000;"
                                + " no wildcard, path or trailing slash)");
            }
        }

        return origins;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.allowed-origins:http://localhost:3000}") List<String> allowedOrigins
    ) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(validate(allowedOrigins));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setAllowCredentials(false);   // tokens travel in a header, not cookies
        configuration.setMaxAge(Duration.ofHours(1));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}