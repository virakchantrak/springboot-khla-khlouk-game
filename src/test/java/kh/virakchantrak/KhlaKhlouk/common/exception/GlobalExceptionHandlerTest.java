package kh.virakchantrak.KhlaKhlouk.common.exception;

import org.junit.jupiter.api.Test;
import org.springframework.dao.InvalidDataAccessApiUsageException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GlobalExceptionHandlerTest {

    @Test
    void unrelatedDataAccessErrorsAreRethrownNotHidden() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        InvalidDataAccessApiUsageException other =
                new InvalidDataAccessApiUsageException("something else went wrong");

        assertThatThrownBy(() -> handler.handleInvalidDataAccessApiUsage(other))
                .isSameAs(other);
    }
}