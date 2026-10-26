package kh.virakchantrak.KhlaKhlouk;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The promise under test: EVERY error response has {code, message, errors}.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ErrorFormatContractIntegrationTest {

    @Autowired MockMvc mockMvc;

    private String bearer;

    @BeforeEach
    void loggedInPlayer() throws Exception {
        String username = "shape-" + UUID.randomUUID();
        register(username);

        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"secret123\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        bearer = "Bearer " + JsonPath.read(body, "$.accessToken");
    }

    @Test
    void missingTokenGives401() throws Exception {
        assertError(mockMvc.perform(get("/api/v1/players/me")), 401, "UNAUTHORIZED");
    }

    @Test
    void invalidTokenGives401() throws Exception {
        assertError(mockMvc.perform(get("/api/v1/players/me")
                .header("Authorization", "Bearer not-a-real-token")), 401, "INVALID_TOKEN");
    }

    @Test
    void wrongRoleGives403() throws Exception {
        assertError(mockMvc.perform(post("/api/v1/games")
                .header("Authorization", bearer)), 403, "ACCESS_DENIED");
    }

    @Test
    void someoneElsesDataGives403() throws Exception {
        assertError(mockMvc.perform(get("/api/v1/players/{id}/bets", UUID.randomUUID())
                .header("Authorization", bearer)), 403, "ACCESS_DENIED");
    }

    @Test
    void failedValidationGives400() throws Exception {
        assertError(mockMvc.perform(post("/api/v1/players")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")), 400, "VALIDATION_ERROR");
    }

    @Test
    void malformedJsonGives400() throws Exception {
        assertError(mockMvc.perform(post("/api/v1/players")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{this is not json")), 400, "BAD_REQUEST");
    }

    @Test
    void invalidIdInThePathGives400() throws Exception {
        assertError(mockMvc.perform(get("/api/v1/games/not-a-uuid")
                .header("Authorization", bearer)), 400, "BAD_REQUEST");
    }

    @Test
    void unsupportedHttpMethodGives405() throws Exception {
        assertError(mockMvc.perform(put("/api/v1/games")
                .header("Authorization", bearer)), 405, "METHOD_NOT_ALLOWED");
    }

    @Test
    void unknownUrlGives404() throws Exception {
        assertError(mockMvc.perform(get("/api/v1/does-not-exist")
                .header("Authorization", bearer)), 404, "NOT_FOUND");
    }

    @Test
    void businessRuleViolationGives409() throws Exception {
        String username = "dup-" + UUID.randomUUID();
        register(username).andExpect(status().isOk());

        assertError(register(username), 409, "USERNAME_ALREADY_EXISTS");
    }

    // ---------- helpers ----------

    private void assertError(ResultActions result, int httpStatus, String code) throws Exception {
        result.andExpect(status().is(httpStatus))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.errors").exists());
    }

    private ResultActions register(String username) throws Exception {
        return mockMvc.perform(post("/api/v1/players")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"secret123\"}"));
    }
}