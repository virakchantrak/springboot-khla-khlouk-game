package kh.virakchantrak.KhlaKhlouk.bet.controller;

import kh.virakchantrak.KhlaKhlouk.auth.config.SecurityConfig;
import kh.virakchantrak.KhlaKhlouk.auth.service.CurrentPlayerService;
import kh.virakchantrak.KhlaKhlouk.auth.service.JwtService;
import kh.virakchantrak.KhlaKhlouk.auth.service.PlayerDetailsService;
import kh.virakchantrak.KhlaKhlouk.bet.service.BetService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;   // same import as your other test
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = BetController.class,
        properties = {"jwt.secret=test-secret", "jwt.expiration=3600000"}
)
@Import(SecurityConfig.class)
class BetControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean BetService betService;
    @MockitoBean CurrentPlayerService currentPlayerService;

    @MockitoBean JwtService jwtService;
    @MockitoBean PlayerDetailsService playerDetailsService;

    private final UUID gameId = UUID.randomUUID();

    @Test
    void placeBetWithoutLoginReturns401() throws Exception {
        mockMvc.perform(post("/api/v1/games/{id}/bets", gameId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"TIGER\",\"amount\":10}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(betService);
    }

    @Test
    @WithMockUser(roles = "PLAYER")
    void placeBetWithThreeDecimalsReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/games/{id}/bets", gameId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"TIGER\",\"amount\":10.005}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors.amount")
                        .value("Bet amount can have at most 2 decimal places"));

        verifyNoInteractions(betService);
    }

    @Test
    @WithMockUser(roles = "PLAYER")
    void placeBetWithZeroAmountReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/games/{id}/bets", gameId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"TIGER\",\"amount\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(betService);
    }

    @Test
    @WithMockUser(roles = "PLAYER")
    void placeBetWithMissingSymbolReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/games/{id}/bets", gameId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":10}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.symbol").exists());

        verifyNoInteractions(betService);
    }

    @Test
    @WithMockUser(roles = "PLAYER")
    void cannotReadAnotherPlayersBets() throws Exception {
        UUID me = UUID.randomUUID();
        UUID someoneElse = UUID.randomUUID();
        when(currentPlayerService.getPlayerId()).thenReturn(me);

        mockMvc.perform(get("/api/v1/players/{id}/bets", someoneElse))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verifyNoInteractions(betService);
    }
}