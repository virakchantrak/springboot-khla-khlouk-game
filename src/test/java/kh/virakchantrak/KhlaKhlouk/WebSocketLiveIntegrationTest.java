package kh.virakchantrak.KhlaKhlouk;

import com.jayway.jsonpath.JsonPath;
import kh.virakchantrak.KhlaKhlouk.player.domain.Player;
import kh.virakchantrak.KhlaKhlouk.player.domain.PlayerRole;
import kh.virakchantrak.KhlaKhlouk.player.repository.PlayerRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.simp.stomp.StompCommand;

import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.assertj.core.api.Assertions.within;

// Runs the real app on a real port, so nothing is rolled back automatically:
// the test cleans up its own rows in @AfterEach.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class WebSocketLiveIntegrationTest {

    @Value("${local.server.port}")
    int port;
    @Autowired
    PlayerRepository playerRepository;
    @Autowired
    SimpUserRegistry userRegistry;

    private final List<String> serverErrors = new CopyOnWriteArrayList<>();
    @Autowired
    JdbcTemplate jdbcTemplate;

    private final HttpClient http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    private final List<String> createdUsernames = new ArrayList<>();
    private final List<UUID> createdGames = new ArrayList<>();
    private final List<StompSession> sessions = new ArrayList<>();

    private WebSocketStompClient stompClient;
    private ThreadPoolTaskScheduler scheduler;

    @BeforeEach
    void startStompClient() {
        scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setDaemon(true);
        scheduler.setThreadNamePrefix("stomp-test-");
        scheduler.initialize();

        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new StringMessageConverter() {
            @Override
            protected boolean supportsMimeType(MessageHeaders headers) {
                // the server sends JSON; the test just wants the raw text
                return true;
            }
        });
        stompClient.setTaskScheduler(scheduler);   // required to track receipts
    }

    @AfterEach
    void cleanUp() {
        for (StompSession session : sessions) {
            if (session.isConnected()) {
                session.disconnect();
            }
        }
        stompClient.stop();
        scheduler.shutdown();

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

    // ---------- the tests ----------

    @Test
    void playerReceivesGameEventsInOrder() throws Exception {
        String adminToken = newAdminToken();
        String playerToken = newPlayerToken();
        String gameId = createGame(adminToken);

        BlockingQueue<String> events =
                subscribe(connect(playerToken), "/topic/games/" + gameId, true);

        call("POST", "/api/v1/games/" + gameId + "/betting", adminToken, null);
        call("POST", "/api/v1/games/" + gameId + "/bets", playerToken,
                "{\"symbol\":\"TIGER\",\"amount\":10.00}");
        String roll = call("POST", "/api/v1/games/" + gameId + "/roll", adminToken, null);

        String opened = next(events);
        String betPlaced = next(events);
        String finished = next(events);

        assertThat((String) JsonPath.read(opened, "$.type")).isEqualTo("BETTING_OPENED");

        assertThat((String) JsonPath.read(betPlaced, "$.type")).isEqualTo("BET_PLACED");
        assertThat((Integer) JsonPath.read(betPlaced, "$.totalBets")).isEqualTo(1);
        assertThat(((Number) JsonPath.read(betPlaced, "$.totalBetAmount")).doubleValue())
                .isCloseTo(10.0, within(0.001));

        assertThat((String) JsonPath.read(finished, "$.type")).isEqualTo("GAME_FINISHED");
        // the event carries exactly the dice the REST response reported
        for (String dice : List.of("dice1", "dice2", "dice3")) {
            assertThat((String) JsonPath.read(finished, "$." + dice))
                    .isEqualTo((String) JsonPath.read(roll, "$." + dice));
        }

        assertThat(events.poll(1, TimeUnit.SECONDS)).isNull();   // nothing extra
    }

    @Test
    void connectionWithoutTokenIsRefused() {
        CompletableFuture<StompSession> attempt = tryConnect(null);

        assertThatThrownBy(() -> attempt.get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class);
    }

    @Test
    void connectionWithBadTokenIsRefused() {
        CompletableFuture<StompSession> attempt = tryConnect("not-a-real-token");

        assertThatThrownBy(() -> attempt.get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class);
    }

    @Test
    void forgedMessageToAGameTopicIsNotBroadcast() throws Exception {
        String playerToken = newPlayerToken();
        String topic = "/topic/games/" + UUID.randomUUID();

        // the sender is also subscribed, so if the broker broadcast the forgery
        // this same session would receive it
        StompSession session = connect(playerToken);
        BlockingQueue<String> events = subscribe(session, topic, true);

        session.send(topic, "{\"type\":\"GAME_FINISHED\",\"dice1\":\"TIGER\"}");

        assertThat(events.poll(2, TimeUnit.SECONDS)).isNull();
    }

    @Test
    void wildcardSubscriptionDoesNotReceiveGameEvents() throws Exception {
        String adminToken = newAdminToken();
        String playerToken = newPlayerToken();

        // the server should reject this subscription (no receipt will ever arrive)
        BlockingQueue<String> spy =
                subscribe(connect(playerToken), "/topic/**", false);

        assertNeverRegistered("/topic/**");

        // a real event happens on a real game
        String gameId = createGame(adminToken);
        call("POST", "/api/v1/games/" + gameId + "/betting", adminToken, null);

        assertThat(spy.poll(2, TimeUnit.SECONDS)).isNull();
    }

    // ---------- STOMP helpers ----------

    private CompletableFuture<StompSession> tryConnect(String token) {
        StompHeaders connectHeaders = new StompHeaders();
        if (token != null) {
            connectHeaders.add("Authorization", "Bearer " + token);
        }

        return stompClient.connectAsync(
                "ws://localhost:" + port + "/ws",
                new WebSocketHttpHeaders(),
                connectHeaders,
                new StompSessionHandlerAdapter() {
                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                        // ERROR frames from the server arrive here
                        serverErrors.add(String.valueOf(headers.getFirst("message")));
                    }

                    @Override
                    public void handleException(
                            StompSession session,
                            StompCommand command,
                            StompHeaders headers,
                            byte[] payload,
                            Throwable exception
                    ) {
                        serverErrors.add("client-side error: " + exception);
                    }

                    @Override
                    public void handleTransportError(StompSession session, Throwable exception) {
                        serverErrors.add("transport error: " + exception);
                    }
                }
        );
    }

    private StompSession connect(String token) throws Exception {
        StompSession session = tryConnect(token).get(5, TimeUnit.SECONDS);
        sessions.add(session);
        return session;
    }

    private BlockingQueue<String> subscribe(
            StompSession session,
            String destination,
            boolean waitUntilRegistered
    ) throws Exception {
        BlockingQueue<String> events = new LinkedBlockingQueue<>();

        session.subscribe(destination, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return String.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                events.add((String) payload);
            }
        });

        if (waitUntilRegistered) {
            awaitServerSubscription(destination);
        }

        return events;
    }

    // the simple broker sends no receipts, so ask the server's own registry instead
    private void awaitServerSubscription(String destination) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);

        while (System.nanoTime() < deadline) {
            if (!userRegistry.findSubscriptions(
                    sub -> destination.equals(sub.getDestination())).isEmpty()) {
                return;
            }
            Thread.sleep(50);
        }

        fail("The server never registered a subscription to " + destination
                + ". Errors reported by the server: " + serverErrors);
    }

    private void assertNeverRegistered(String destination) throws InterruptedException {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);

        while (System.nanoTime() < until) {
            assertThat(userRegistry.findSubscriptions(
                    sub -> destination.equals(sub.getDestination())))
                    .as("subscription to " + destination + " must be refused")
                    .isEmpty();
            Thread.sleep(50);
        }
    }

    private String next(BlockingQueue<String> events) throws InterruptedException {
        String event = events.poll(5, TimeUnit.SECONDS);
        assertThat(event)
                .as("an event arrived within 5 seconds; errors seen: " + serverErrors)
                .isNotNull();
        return event;
    }

    // ---------- REST helpers (plain HTTP, no mocks) ----------

    private String call(String method, String path, String token, String json)
            throws Exception {
        HttpRequest.Builder request = HttpRequest
                .newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json");

        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }

        request.method(method, json == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json));

        HttpResponse<String> response =
                http.send(request.build(), HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode())
                .as(method + " " + path + " -> " + response.body())
                .isEqualTo(200);

        return response.body();
    }

    private String newPlayerToken() throws Exception {
        String username = "ws-" + UUID.randomUUID();
        createdUsernames.add(username);

        call("POST", "/api/v1/players", null,
                "{\"username\":\"" + username + "\",\"password\":\"secret123\"}");

        return login(username);
    }

    private String newAdminToken() throws Exception {
        String username = "ws-admin-" + UUID.randomUUID();
        createdUsernames.add(username);

        call("POST", "/api/v1/players", null,
                "{\"username\":\"" + username + "\",\"password\":\"secret123\"}");

        Player player = playerRepository.findByUsername(username).orElseThrow();
        player.setRole(PlayerRole.ADMIN);
        playerRepository.save(player);

        return login(username);
    }

    private String login(String username) throws Exception {
        String body = call("POST", "/api/v1/auth/login", null,
                "{\"username\":\"" + username + "\",\"password\":\"secret123\"}");
        return JsonPath.read(body, "$.accessToken");
    }

    private String createGame(String adminToken) throws Exception {
        String body = call("POST", "/api/v1/games", adminToken, null);
        String gameId = JsonPath.read(body, "$.id");
        createdGames.add(UUID.fromString(gameId));
        return gameId;
    }

    @Test
    void playersAreToldWhenAGameIsCancelled() throws Exception {
        String adminToken = newAdminToken();
        String playerToken = newPlayerToken();
        String gameId = createGame(adminToken);

        BlockingQueue<String> events =
                subscribe(connect(playerToken), "/topic/games/" + gameId, true);

        call("POST", "/api/v1/games/" + gameId + "/betting", adminToken, null);
        call("POST", "/api/v1/games/" + gameId + "/bets", playerToken,
                "{\"symbol\":\"TIGER\",\"amount\":10.00}");
        call("POST", "/api/v1/games/" + gameId + "/cancel", adminToken, null);

        next(events);   // BETTING_OPENED
        next(events);   // BET_PLACED
        String cancelled = next(events);

        assertThat((String) JsonPath.read(cancelled, "$.type")).isEqualTo("GAME_CANCELLED");
        assertThat((String) JsonPath.read(cancelled, "$.status")).isEqualTo("CANCELLED");
        assertThat((Integer) JsonPath.read(cancelled, "$.totalBets")).isEqualTo(1);
        assertThat(((Number) JsonPath.read(cancelled, "$.totalBetAmount")).doubleValue())
                .isCloseTo(10.0, within(0.001));

        assertThat(events.poll(1, TimeUnit.SECONDS)).isNull();   // nothing extra
    }
}