package kh.virakchantrak.KhlaKhlouk.game.controller;

import kh.virakchantrak.KhlaKhlouk.auth.config.SecurityConfig;
import kh.virakchantrak.KhlaKhlouk.auth.service.CurrentPlayerService;
import kh.virakchantrak.KhlaKhlouk.auth.service.JwtService;
import kh.virakchantrak.KhlaKhlouk.auth.service.PlayerDetailsService;
import kh.virakchantrak.KhlaKhlouk.common.exception.BusinessException;
import kh.virakchantrak.KhlaKhlouk.game.domain.Game;
import kh.virakchantrak.KhlaKhlouk.game.domain.GameStatus;
import kh.virakchantrak.KhlaKhlouk.game.service.GameService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;   // let your IDE confirm this import
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

@WebMvcTest(
        controllers = GameController.class,
        properties = {"jwt.secret=rSwGVXwk4Laqibfv+DEuqYVLEAhuK5MV5E0hgEqZCW528T9WsS633m0gQX5skNX3", "jwt.expiration=3600000"}
)
@Import(SecurityConfig.class)
class GameControllerSecurityTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean GameService gameService;

    @MockitoBean JwtService jwtService;
    @MockitoBean PlayerDetailsService playerDetailsService;

    @MockitoBean CurrentPlayerService currentPlayerService;

    @Test
    void createGameWithoutLoginReturns401() throws Exception {
        mockMvc.perform(post("/api/v1/games"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));;

        verifyNoInteractions(gameService);
    }

    @Test
    @WithMockUser(roles = "PLAYER")
    void createGameAsNormalPlayerReturns403() throws Exception {
        mockMvc.perform(post("/api/v1/games"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.message").value("Access denied"));

        verifyNoInteractions(gameService);
    }

    @Test
    @WithMockUser(roles = "PLAYER")
    void rollGameAsNormalPlayerReturns403() throws Exception {
        mockMvc.perform(post("/api/v1/games/{id}/roll", UUID.randomUUID()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(gameService);
    }

    @Test
    @WithMockUser(roles = {"PLAYER", "ADMIN"})
    void createGameAsAdminReturns200() throws Exception {
        Game game = new Game();
        game.setId(UUID.randomUUID());
        game.setStatus(GameStatus.WAITING);

        when(gameService.createGame()).thenReturn(game);

        mockMvc.perform(post("/api/v1/games"))
                .andExpect(status().isOk());

        verify(gameService).createGame();
    }

    @Test
    @WithMockUser(roles = "PLAYER")
    void invalidIdInThePathReturns400InTheStandardFormat() throws Exception {
        mockMvc.perform(get("/api/v1/games/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Malformed or invalid request"));
    }

    @Test
    @WithMockUser(roles = "PLAYER")
    void unsupportedMethodReturns405InTheStandardFormat() throws Exception {
        mockMvc.perform(put("/api/v1/games"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    @WithMockUser(roles = "PLAYER")
    void unknownUrlReturns404InTheStandardFormat() throws Exception {
        mockMvc.perform(get("/api/v1/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void cancelGameWithoutLoginReturns401() throws Exception {
        mockMvc.perform(post("/api/v1/games/{id}/cancel", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(gameService);
    }

    @Test
    @WithMockUser(roles = "PLAYER")
    void cancelGameAsNormalPlayerReturns403() throws Exception {
        mockMvc.perform(post("/api/v1/games/{id}/cancel", UUID.randomUUID()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(gameService);
    }

    @Test
    @WithMockUser(roles = {"PLAYER", "ADMIN"})
    void cancelGameAsAdminReturns200() throws Exception {
        UUID id = UUID.randomUUID();
        Game game = new Game();
        game.setId(id);
        game.setStatus(GameStatus.CANCELLED);

        when(gameService.cancelGame(id)).thenReturn(game);

        mockMvc.perform(post("/api/v1/games/{id}/cancel", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        verify(gameService).cancelGame(id);
    }

    @Test
    @WithMockUser(roles = {"PLAYER", "ADMIN"})
    void cancellingAFinishedGameReturns400() throws Exception {
        UUID id = UUID.randomUUID();

        when(gameService.cancelGame(id)).thenThrow(new BusinessException(
                "GAME_NOT_CANCELLABLE",
                "Only a game that has not been rolled can be cancelled",
                HttpStatus.BAD_REQUEST));

        mockMvc.perform(post("/api/v1/games/{id}/cancel", id))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GAME_NOT_CANCELLABLE"));
    }
}