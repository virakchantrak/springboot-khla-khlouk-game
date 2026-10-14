package kh.virakchantrak.KhlaKhlouk.player.controller.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CreatePlayerRequestValidationTest {

    private static final Validator validator =
            Validation.buildDefaultValidatorFactory().getValidator();

    private boolean usernameIsValid(String username) {
        CreatePlayerRequest request = new CreatePlayerRequest(username, "secret123");

        return validator.validate(request).stream()
                .noneMatch(v -> v.getPropertyPath().toString().equals("username"));
    }

    @Test
    void acceptsNormalUsernames() {
        assertThat(usernameIsValid("alice")).isTrue();
        assertThat(usernameIsValid("alice_01")).isTrue();
        assertThat(usernameIsValid("Bob.Smith-2")).isTrue();
        assertThat(usernameIsValid("abc")).isTrue();
        assertThat(usernameIsValid("a".repeat(50))).isTrue();
    }

    @Test
    void rejectsTooShortUsernames() {
        assertThat(usernameIsValid("ab")).isFalse();
        assertThat(usernameIsValid("")).isFalse();
    }

    @Test
    void rejectsTooLongUsernames() {
        assertThat(usernameIsValid("a".repeat(51))).isFalse();
    }

    @Test
    void rejectsSpaces() {
        assertThat(usernameIsValid("has space")).isFalse();
        assertThat(usernameIsValid(" alice")).isFalse();
    }

    @Test
    void rejectsSymbols() {
        assertThat(usernameIsValid("alice!")).isFalse();
        assertThat(usernameIsValid("a@b.com")).isFalse();
    }

    @Test
    void rejectsNonAsciiLetters() {
        // three Khmer letters, written as escapes so the file encoding can't matter
        assertThat(usernameIsValid("\u179f\u17bd\u179f")).isFalse();
    }
}