package kh.virakchantrak.KhlaKhlouk;

import kh.virakchantrak.KhlaKhlouk.player.repository.PlayerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;   // same import as your other tests
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// a small limit, so the test can reach it; it also proves the setting is really read
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "app.registration.max-per-ip-per-hour=3")
@Transactional
class RegistrationLimitIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired PlayerRepository playerRepository;

    @Test
    void fourthRegistrationFromTheSameAddressIsBlockedBeforeAnythingIsCreated() throws Exception {
        for (int i = 0; i < 3; i++) {
            registerFrom("203.0.113.20", "limit-" + UUID.randomUUID())
                    .andExpect(status().isOk());
        }

        String blockedName = "limit-blocked-" + UUID.randomUUID();

        registerFrom("203.0.113.20", blockedName)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_REGISTRATIONS"));

        // the request was stopped before any account was created
        assertThat(playerRepository.findByUsername(blockedName)).isEmpty();

        // another address is not affected
        registerFrom("198.51.100.20", "limit-other-" + UUID.randomUUID())
                .andExpect(status().isOk());
    }

    private ResultActions registerFrom(String ip, String username) throws Exception {
        return mockMvc.perform(post("/api/v1/players")
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"secret123\"}"));
    }
}