package kh.virakchantrak.KhlaKhlouk.auth.service;

import kh.virakchantrak.KhlaKhlouk.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;

public final class PasswordPolicy {

    // BCrypt only uses the first 72 BYTES of a password
    public static final int MAX_BYTES = 72;

    private PasswordPolicy() {
    }

    public static void checkLength(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new BusinessException(
                    "PASSWORD_TOO_LONG",
                    "Password is too long (maximum " + MAX_BYTES
                            + " bytes; non-English characters use more than one byte each)",
                    HttpStatus.BAD_REQUEST
            );
        }
    }
}