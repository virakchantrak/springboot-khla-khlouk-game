package kh.virakchantrak.KhlaKhlouk;

import com.jayway.jsonpath.JsonPath;
import kh.virakchantrak.KhlaKhlouk.player.domain.Player;
import kh.virakchantrak.KhlaKhlouk.player.repository.PlayerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;   // same import as your other tests
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class UsernameCaseIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired PlayerRepository playerRepository;

    @Test
    void usernamesDifferingOnlyInCaseCannotBothRegister() throws Exception {
        String suffix = UUID.randomUUID().toString();

        register("Case-" + suffix).andExpect(status().isOk());

        register("case-" + suffix)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USERNAME_ALREADY_EXISTS"));
    }

    @Test
    void loginIgnoresUsernameCaseButTheAccountKeepsItsSpelling() throws Exception {
        String suffix = UUID.randomUUID().toString();
        register("MixedCase-" + suffix).andExpect(status().isOk());

        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"mixedcase-" + suffix
                                + "\",\"password\":\"secret123\"}"))
                .andExpect(status().isOk())
                .andReturn();

        String token = JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken");

        mockMvc.perform(get("/api/v1/players/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("MixedCase-" + suffix));
    }

    @Test
    void databaseItselfRejectsCaseInsensitiveDuplicates() {
        String suffix = UUID.randomUUID().toString();

        playerRepository.saveAndFlush(player("Db-" + suffix));

        // bypasses the service on purpose: this proves the index, not the Java check
        assertThatThrownBy(() -> playerRepository.saveAndFlush(player("db-" + suffix)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private ResultActions register(String username) throws Exception {
        return mockMvc.perform(post("/api/v1/players")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"secret123\"}"));
    }

    private Player player(String username) {
        Player player = new Player();
        player.setUsername(username);
        player.setPasswordHash("not-a-real-hash");
        player.setBalance(BigDecimal.ONE);
        return player;
    }
}