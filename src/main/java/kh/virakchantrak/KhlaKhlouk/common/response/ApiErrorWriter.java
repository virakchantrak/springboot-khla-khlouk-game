package kh.virakchantrak.KhlaKhlouk.common.response;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Writes an {code, message, errors} body from code that runs outside the
 * controllers (security filters and handlers).
 *
 * Only pass fixed text for code and message, never user input: the strings
 * are placed into the JSON as they are.
 */
public final class ApiErrorWriter {

    private ApiErrorWriter() {
    }

    public static void write(
            HttpServletResponse response,
            HttpStatus status,
            String code,
            String message
    ) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(
                "{\"code\":\"" + code + "\",\"message\":\"" + message + "\",\"errors\":{}}");
    }
}