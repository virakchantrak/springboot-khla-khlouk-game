package kh.virakchantrak.KhlaKhlouk.auth.service;

import kh.virakchantrak.KhlaKhlouk.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordPolicyTest {

    @Test
    void acceptsExactly72AsciiCharacters() {
        assertThatCode(() -> PasswordPolicy.checkLength("a".repeat(72)))
                .doesNotThrowAnyException();
    }

    @Test
    void rejects73AsciiCharacters() {
        assertThatThrownBy(() -> PasswordPolicy.checkLength("a".repeat(73)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "PASSWORD_TOO_LONG");
    }

    @Test
    void countsBytesNotCharacters() {
        // one Khmer letter is 3 bytes in UTF-8: 24 letters = 72 bytes, 25 = 75 bytes
        String khmerLetter = "\u1780";

        assertThatCode(() -> PasswordPolicy.checkLength(khmerLetter.repeat(24)))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> PasswordPolicy.checkLength(khmerLetter.repeat(25)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "PASSWORD_TOO_LONG");
    }
}