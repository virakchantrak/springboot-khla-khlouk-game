package kh.virakchantrak.KhlaKhlouk.auth.config;

import kh.virakchantrak.KhlaKhlouk.auth.controller.AuthController;
import kh.virakchantrak.KhlaKhlouk.auth.service.AuthService;
import kh.virakchantrak.KhlaKhlouk.auth.service.CurrentPlayerService;
import kh.virakchantrak.KhlaKhlouk.auth.service.JwtService;
import kh.virakchantrak.KhlaKhlouk.auth.service.PlayerDetailsService;
import kh.virakchantrak.KhlaKhlouk.player.controller.PlayerController;
import kh.virakchantrak.KhlaKhlouk.player.domain.Player;
import kh.virakchantrak.KhlaKhlouk.player.service.PlayerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;   // same import as your other tests
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = {AuthController.class, PlayerController.class},
        properties = {"jwt.secret=rSwGVXwk4Laqibfv+DEuqYVLEAhuK5MV5E0hgEqZCW528T9WsS633m0gQX5skNX3", "jwt.expiration=3600000"}
)
@Import(SecurityConfig.class)
class PublicEndpointsSecurityTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean PlayerService playerService;
    @MockitoBean CurrentPlayerService currentPlayerService;
    @MockitoBean AuthService authService;
    @MockitoBean AuthenticationManager authenticationManager;
    @MockitoBean JwtService jwtService;
    @MockitoBean PlayerDetailsService playerDetailsService;

    // ---------- must NOT be public any more ----------

    @Test
    void listingPlayersWithoutLoginReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/players"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginEndpointOnlyAcceptsPostPublicly() throws Exception {
        mockMvc.perform(get("/api/v1/auth/login"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownPathsAreProtectedByDefault() throws Exception {
        mockMvc.perform(get("/api/v1/does-not-exist"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- must STAY public ----------

    @Test
    void registrationStaysPublic() throws Exception {
        Player created = new Player();
        created.setUsername("alice");
        created.setBalance(new BigDecimal("1000.00"));
        when(playerService.createPlayer("alice", "secret123")).thenReturn(created);

        mockMvc.perform(post("/api/v1/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"secret123\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void loginStaysPublic() throws Exception {
        // an empty body is rejected by validation (400), which proves the
        // request got past security and reached the controller
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}