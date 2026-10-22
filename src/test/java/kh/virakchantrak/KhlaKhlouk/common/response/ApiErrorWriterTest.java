package kh.virakchantrak.KhlaKhlouk.common.response;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class ApiErrorWriterTest {

    @Test
    void writesStatusContentTypeAndTheStandardBody() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        ApiErrorWriter.write(response, HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Access denied");

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getContentAsString()).isEqualTo(
                "{\"code\":\"ACCESS_DENIED\",\"message\":\"Access denied\",\"errors\":{}}");
    }
}