package kh.virakchantrak.KhlaKhlouk;

import com.jayway.jsonpath.JsonPath;
import kh.virakchantrak.KhlaKhlouk.player.domain.Player;
import kh.virakchantrak.KhlaKhlouk.player.domain.PlayerRole;
import kh.virakchantrak.KhlaKhlouk.player.repository.PlayerRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;   // same import as your other tests
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// NOT @Transactional on purpose: a test-wide transaction would keep a session open
// and hide lazy-loading problems. Data is committed for real and deleted afterwards.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.jpa.open-in-view=false")
class OpenInViewOffIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired PlayerRepository playerRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    private final List<String> createdUsernames = new ArrayList<>();
    private final List<UUID> createdGames = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (UUID gameId : createdGames) {
            jdbcTemplate.update("DELETE FROM bets WHERE game_id = ?", gameId);
            jdbcTemplate.update("DELETE FROM game_results WHERE game_id = ?", gameId);
            jdbcTemplate.update("DELETE FROM games WHERE id = ?", gameId);
        }
        for (String username : createdUsernames) {
            jdbcTemplate.update(
                    "DELETE FROM wallet_transactions WHERE player_id IN "
                            + "(SELECT id FROM players WHERE username = ?)", username);
            jdbcTemplate.update("DELETE FROM players WHERE username = ?", username);
        }
    }

    @Test
    void everyEndpointThatReadsAssociationsWorksWithOpenInViewOff() throws Exception {
        String adminToken = newToken("oiv-admin-" + UUID.randomUUID(), true);
        String playerToken = newToken("oiv-player-" + UUID.randomUUID(), false);

        String gameId = JsonPath.read(
                body(mockMvc.perform(post("/api/v1/games")
                                .header("Authorization", "Bearer " + adminToken))
                        .andExpect(status().isOk())),
                "$.id");
        createdGames.add(UUID.fromString(gameId));

        mockMvc.perform(post("/api/v1/games/{id}/betting", gameId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/games/{id}/bets", gameId)
                        .header("Authorization", "Bearer " + playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"TIGER\",\"amount\":10.00}"))
                .andExpect(status().isOk());

        String roll = body(mockMvc.perform(post("/api/v1/games/{id}/roll", gameId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINISHED")));

        // --- the reads that touch lazy associations ---

        mockMvc.perform(get("/api/v1/games")
                        .header("Authorization", "Bearer " + playerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());

        mockMvc.perform(get("/api/v1/games/{id}", gameId)
                        .header("Authorization", "Bearer " + playerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINISHED"))
                .andExpect(jsonPath("$.totalBets").value(1));

        mockMvc.perform(get("/api/v1/games/{id}/result", gameId)
                        .header("Authorization", "Bearer " + playerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dice1").value((String) JsonPath.read(roll, "$.dice1")));

        String playerId = JsonPath.read(
                body(mockMvc.perform(get("/api/v1/players/me")
                                .header("Authorization", "Bearer " + playerToken))
                        .andExpect(status().isOk())),
                "$.id");

        mockMvc.perform(get("/api/v1/players/{id}/bets", playerId)
                        .header("Authorization", "Bearer " + playerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].gameId").value(gameId))
                .andExpect(jsonPath("$.content[0].playerId").value(playerId));

        mockMvc.perform(get("/api/v1/wallet/transactions")
                        .header("Authorization", "Bearer " + playerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").isNumber());
    }

    // ---------- helpers ----------

    private String newToken(String username, boolean admin) throws Exception {
        createdUsernames.add(username);

        mockMvc.perform(post("/api/v1/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"secret123\"}"))
                .andExpect(status().isOk());

        if (admin) {
            Player player = playerRepository.findByUsername(username).orElseThrow();
            player.setRole(PlayerRole.ADMIN);
            playerRepository.save(player);
        }

        String body = body(mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"secret123\"}"))
                .andExpect(status().isOk()));

        return JsonPath.read(body, "$.accessToken");
    }

    private String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }
}