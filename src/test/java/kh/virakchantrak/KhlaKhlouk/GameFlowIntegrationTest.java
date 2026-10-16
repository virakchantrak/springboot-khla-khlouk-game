package kh.virakchantrak.KhlaKhlouk;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.jayway.jsonpath.JsonPath;
import kh.virakchantrak.KhlaKhlouk.game.domain.Symbol;
import kh.virakchantrak.KhlaKhlouk.player.domain.Player;
import kh.virakchantrak.KhlaKhlouk.player.domain.PlayerRole;
import kh.virakchantrak.KhlaKhlouk.player.repository.PlayerRepository;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class GameFlowIntegrationTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    PlayerRepository playerRepository;

    private ListAppender<ILoggingEvent> captureAuditLog() {
        ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("SECURITY_AUDIT");
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private void stopCapturing(ListAppender<ILoggingEvent> appender) {
        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger("SECURITY_AUDIT"))
                .detachAppender(appender);
    }

    @Test
    void registrationPasswordChangesAndAdminActionsAreAudited() throws Exception {
        ListAppender<ILoggingEvent> appender = captureAuditLog();

        try {
            String username = "audited2-" + UUID.randomUUID();
            register(username);
            makeAdmin(username);
            String token = login(username);

            mockMvc.perform(post("/api/v1/games")
                            .header("Authorization", bearer(token)))
                    .andExpect(status().isOk());

            mockMvc.perform(put("/api/v1/auth/password")
                            .header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"currentPassword\":\"wrong-current\","
                                    + "\"newPassword\":\"new-secret-456\"}"))
                    .andExpect(status().isBadRequest());

            mockMvc.perform(put("/api/v1/auth/password")
                            .header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"currentPassword\":\"secret123\","
                                    + "\"newPassword\":\"new-secret-456\"}"))
                    .andExpect(status().isNoContent());

            List<String> messages = appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .toList();

            assertThat(messages).anyMatch(m ->
                    m.contains("event=PLAYER_REGISTERED")
                            && m.contains("username=\"" + username + "\""));
            assertThat(messages).anyMatch(m ->
                    m.contains("event=ADMIN_ACTION")
                            && m.contains("action=\"CREATE_GAME\""));
            assertThat(messages).anyMatch(m -> m.contains("event=PASSWORD_CHANGE_FAILED"));
            assertThat(messages).anyMatch(m -> m.contains("event=PASSWORD_CHANGED"));

            // no password ever reaches the log
            assertThat(messages).noneMatch(m ->
                    m.contains("wrong-current")
                            || m.contains("new-secret-456")
                            || m.contains("secret123"));

        } finally {
            stopCapturing(appender);
        }
    }

    @Test
    void failedLoginAndDeniedActionAreWrittenToTheAuditLog() throws Exception {
        ch.qos.logback.classic.Logger auditLogger =
                (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("SECURITY_AUDIT");
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        auditLogger.addAppender(appender);

        try {
            String username = "audited-" + UUID.randomUUID();
            register(username);
            String token = login(username);

            // a wrong password from a known address
            loginFrom("203.0.113.55", username, "wrong-password")
                    .andExpect(status().isUnauthorized());

            // a normal player trying an admin-only action
            mockMvc.perform(post("/api/v1/games")
                            .header("Authorization", bearer(token)))
                    .andExpect(status().isForbidden());

            List<String> messages = appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .toList();

            assertThat(messages).anyMatch(m ->
                    m.contains("event=LOGIN_FAILED")
                            && m.contains("ip=\"203.0.113.55\"")
                            && m.contains("username=\"" + username + "\""));

            assertThat(messages).anyMatch(m ->
                    m.contains("event=ACCESS_DENIED")
                            && m.contains("username=\"" + username + "\"")
                            && m.contains("path=\"/api/v1/games\""));

            // secrets never reach the log
            assertThat(messages).noneMatch(m -> m.contains("wrong-password"));
            assertThat(messages).noneMatch(m -> m.contains(token));

        } finally {
            auditLogger.detachAppender(appender);
        }
    }

    @Test
    void oldTokenStopsWorkingAfterPasswordChange() throws Exception {
        String username = "changer-" + UUID.randomUUID();
        register(username);
        String oldToken = login(username);

        mockMvc.perform(put("/api/v1/auth/password")
                        .header("Authorization", bearer(oldToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"secret123\","
                                + "\"newPassword\":\"new-secret-456\"}"))
                .andExpect(status().isNoContent());

        // the old token no longer works
        mockMvc.perform(get("/api/v1/players/me")
                        .header("Authorization", bearer(oldToken)))
                .andExpect(status().isUnauthorized());

        // logging in with the new password gives a working token
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username
                                + "\",\"password\":\"new-secret-456\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String newToken = JsonPath.read(body(result), "$.accessToken");

        mockMvc.perform(get("/api/v1/players/me")
                        .header("Authorization", bearer(newToken)))
                .andExpect(status().isOk());
    }

    @Test
    void loginIsBlockedAfterFiveWrongPasswords() throws Exception {
        String username = "victim-" + UUID.randomUUID();
        register(username);

        for (int i = 0; i < 5; i++) {
            loginFrom("203.0.113.7", username, "wrong-password")
                    .andExpect(status().isUnauthorized());
        }

        // the 6th attempt is blocked, even with the correct password
        loginFrom("203.0.113.7", username, "secret123")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_LOGIN_ATTEMPTS"));

        // the real owner, from another address, is not locked out
        loginFrom("198.51.100.9", username, "secret123")
                .andExpect(status().isOk());
    }

    private ResultActions loginFrom(String ip, String username, String password)
            throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username
                        + "\",\"password\":\"" + password + "\"}"));
    }

    @Test
    void playerBetsOnEverySymbolAndGetsCorrectPayout() throws Exception {
        String adminName = "admin-" + UUID.randomUUID();
        String playerName = "player-" + UUID.randomUUID();

        // two accounts; one becomes admin (there is deliberately no API for this)
        register(adminName);
        register(playerName);
        makeAdmin(adminName);

        String adminToken = login(adminName);
        String playerToken = login(playerName);

        // admin creates the game and opens betting
        String gameId = JsonPath.read(
                body(mockMvc.perform(post("/api/v1/games")
                                .header("Authorization", bearer(adminToken)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.status").value("WAITING"))
                        .andReturn()),
                "$.id");

        mockMvc.perform(post("/api/v1/games/{id}/betting", gameId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BETTING"));

        // player bets 10 on each of the six symbols = 60 in total
        for (Symbol symbol : Symbol.values()) {
            mockMvc.perform(post("/api/v1/games/{id}/bets", gameId)
                            .header("Authorization", bearer(playerToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"symbol\":\"" + symbol + "\",\"amount\":10.00}"))
                    .andExpect(status().isOk());
        }
        assertThat(balanceOf(playerToken)).isCloseTo(940.0, within(0.001));

        // admin rolls
        String rollJson = body(mockMvc.perform(post("/api/v1/games/{id}/roll", gameId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINISHED"))
                .andReturn());

        String d1 = JsonPath.read(rollJson, "$.dice1");
        String d2 = JsonPath.read(rollJson, "$.dice2");
        String d3 = JsonPath.read(rollJson, "$.dice3");

        // the three dice show d different symbols; every shown symbol wins
        int distinct = new HashSet<>(List.of(d1, d2, d3)).size();
        double expectedBalance = 940.0 + 10.0 * (3 + distinct);

        assertThat(balanceOf(playerToken)).isCloseTo(expectedBalance, within(0.001));

        // wallet history: 6 BET entries + one PAYOUT per winning symbol
        mockMvc.perform(get("/api/v1/wallet/transactions")
                        .header("Authorization", bearer(playerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(6 + distinct));

        // betting is closed now
        mockMvc.perform(post("/api/v1/games/{id}/bets", gameId)
                        .header("Authorization", bearer(playerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"TIGER\",\"amount\":10.00}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GAME_NOT_ACCEPTING_BETS"));
    }

    // ---------- helpers ----------

    private void register(String username) throws Exception {
        mockMvc.perform(post("/api/v1/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"secret123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(1000.0));
    }

    private void makeAdmin(String username) {
        Player player = playerRepository.findByUsername(username).orElseThrow();
        player.setRole(PlayerRole.ADMIN);
        playerRepository.saveAndFlush(player);
    }

    private String login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"secret123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(body(result), "$.accessToken");
    }

    private double balanceOf(String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/players/me")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn();
        return ((Number) JsonPath.read(body(result), "$.balance")).doubleValue();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String body(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString();
    }

    @Test
    void passwordChangeIsBlockedAfterFiveWrongCurrentPasswords() throws Exception {
        String username = "guesser-" + UUID.randomUUID();
        register(username);
        String token = login(username);

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(put("/api/v1/auth/password")
                            .header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"currentPassword\":\"wrong-" + i + "\","
                                    + "\"newPassword\":\"new-secret-456\"}"))
                    .andExpect(status().isBadRequest());
        }

        // the 6th attempt is blocked, even with the correct current password
        mockMvc.perform(put("/api/v1/auth/password")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"secret123\","
                                + "\"newPassword\":\"new-secret-456\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_PASSWORD_ATTEMPTS"));

        // nothing changed: the original password still logs in
        login(username);
    }

    @Test
    void preflightFromAllowedOriginIsAccepted() throws Exception {
        mockMvc.perform(options("/api/v1/games")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    assertThat(result.getResponse().getHeader("Access-Control-Allow-Origin"))
                            .isEqualTo("http://localhost:3000");
                    assertThat(result.getResponse().getHeader("Access-Control-Allow-Methods"))
                            .contains("POST");
                });
    }

    @Test
    void preflightFromUnknownOriginIsRejected() throws Exception {
        mockMvc.perform(options("/api/v1/games")
                        .header("Origin", "https://evil.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden())
                .andExpect(result -> assertThat(
                        result.getResponse().getHeader("Access-Control-Allow-Origin")).isNull());
    }

    @Test
    void realRequestFromAllowedOriginGetsCorsHeader() throws Exception {
        String username = "cors-" + UUID.randomUUID();
        register(username);
        String token = login(username);

        mockMvc.perform(get("/api/v1/players/me")
                        .header("Authorization", bearer(token))
                        .header("Origin", "http://localhost:3000"))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(
                        result.getResponse().getHeader("Access-Control-Allow-Origin"))
                        .isEqualTo("http://localhost:3000"));
    }

    @Test
    void realRequestFromUnknownOriginIsRejected() throws Exception {
        String username = "cors2-" + UUID.randomUUID();
        register(username);
        String token = login(username);

        mockMvc.perform(get("/api/v1/players/me")
                        .header("Authorization", bearer(token))
                        .header("Origin", "https://evil.example"))
                .andExpect(status().isForbidden());
    }

    @Test
    void requestsWithoutAnOriginAreUnaffected() throws Exception {
        // Postman, curl and mobile apps send no Origin header
        String username = "cors3-" + UUID.randomUUID();
        register(username);
        String token = login(username);

        mockMvc.perform(get("/api/v1/players/me")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk());
    }

    @Test
    void pageSizeIsCappedAt100() throws Exception {
        String username = "pager-" + UUID.randomUUID();
        register(username);
        String token = login(username);

        mockMvc.perform(get("/api/v1/wallet/transactions?size=100000")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    void unknownSortPropertyReturns400NotAServerError() throws Exception {
        String username = "sorter-" + UUID.randomUUID();
        register(username);
        String token = login(username);

        mockMvc.perform(get("/api/v1/games?sort=doesNotExist")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SORT"));
    }

    @Test
    void validSortStillWorks() throws Exception {
        String username = "sorter2-" + UUID.randomUUID();
        register(username);
        String token = login(username);

        mockMvc.perform(get("/api/v1/games?sort=createdAt,desc")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk());
    }

    @Test
    void playersOnlySeeTheirOwnWalletEntries() throws Exception {
        String adminName = "wallet-admin-" + UUID.randomUUID();
        String aliceName = "wallet-a-" + UUID.randomUUID();
        String bobName = "wallet-b-" + UUID.randomUUID();

        register(adminName);
        register(aliceName);
        register(bobName);
        makeAdmin(adminName);

        String adminToken = login(adminName);
        String aliceToken = login(aliceName);
        String bobToken = login(bobName);

        // admin opens a game; only Alice bets
        String gameId = JsonPath.read(
                body(mockMvc.perform(post("/api/v1/games")
                                .header("Authorization", bearer(adminToken)))
                        .andExpect(status().isOk())
                        .andReturn()),
                "$.id");

        mockMvc.perform(post("/api/v1/games/{id}/betting", gameId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/games/{id}/bets", gameId)
                        .header("Authorization", bearer(aliceToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"TIGER\",\"amount\":10.00}"))
                .andExpect(status().isOk());

        // Alice sees her own bet
        mockMvc.perform(get("/api/v1/wallet/transactions")
                        .header("Authorization", bearer(aliceToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].type").value("BET"))
                .andExpect(jsonPath("$.content[0].amount").value(10.0));

        // Bob sees nothing of hers
        mockMvc.perform(get("/api/v1/wallet/transactions")
                        .header("Authorization", bearer(bobToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.content").isEmpty());

        // Bob cannot ask for Alice's entries by passing her id
        String aliceId = JsonPath.read(
                body(mockMvc.perform(get("/api/v1/players/me")
                                .header("Authorization", bearer(aliceToken)))
                        .andExpect(status().isOk())
                        .andReturn()),
                "$.id");

        mockMvc.perform(get("/api/v1/wallet/transactions?playerId=" + aliceId)
                        .header("Authorization", bearer(bobToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        // and without a login there is nothing at all
        mockMvc.perform(get("/api/v1/wallet/transactions"))
                .andExpect(status().isUnauthorized());
    }
}