package kh.virakchantrak.KhlaKhlouk.auth.audit;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityAuditTest {

    private ch.qos.logback.classic.Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void attachAppender() {
        logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("SECURITY_AUDIT");
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
    }

    private String onlyMessage() {
        assertThat(appender.list).hasSize(1);
        return appender.list.get(0).getFormattedMessage();
    }

    @Test
    void failedLoginIsLoggedAsWarningWithIpAndUsername() {
        SecurityAudit.loginFailed("1.2.3.4", "alice");

        assertThat(appender.list.get(0).getLevel()).isEqualTo(Level.WARN);
        assertThat(onlyMessage())
                .isEqualTo("event=LOGIN_FAILED ip=\"1.2.3.4\" username=\"alice\"");
    }

    @Test
    void successfulLoginIsLoggedAsInfo() {
        SecurityAudit.loginSucceeded("1.2.3.4", "alice");

        assertThat(appender.list.get(0).getLevel()).isEqualTo(Level.INFO);
        assertThat(onlyMessage()).contains("event=LOGIN_SUCCESS");
    }

    @Test
    void lineBreaksInUsernameCannotForgeExtraLogLines() {
        SecurityAudit.loginFailed("1.1.1.1", "bob\nevent=LOGIN_SUCCESS username=\"admin\"");

        String message = onlyMessage();

        assertThat(message).doesNotContain("\n").doesNotContain("\r");
        // only our own four quotes remain: two around ip, two around username
        assertThat(message.chars().filter(c -> c == '"').count()).isEqualTo(4);
    }

    @Test
    void veryLongValuesAreTruncated() {
        SecurityAudit.loginFailed("1.1.1.1", "a".repeat(5_000));

        assertThat(onlyMessage().length()).isLessThan(250);
    }

    @Test
    void missingValuesAreShownAsDash() {
        SecurityAudit.accessDenied("1.1.1.1", null, "POST", "/api/v1/games");

        assertThat(onlyMessage()).contains("username=\"-\"");
    }

    @Test
    void accessDeniedContainsWhoWhatAndWhere() {
        SecurityAudit.accessDenied("1.1.1.1", "alice", "POST", "/api/v1/games");

        assertThat(onlyMessage()).isEqualTo(
                "event=ACCESS_DENIED ip=\"1.1.1.1\" username=\"alice\""
                        + " method=\"POST\" path=\"/api/v1/games\"");
    }

    @Test
    void invalidTokenLineHasNoTokenAtAll() {
        SecurityAudit.invalidToken("1.1.1.1", "GET", "/api/v1/players/me");

        assertThat(onlyMessage()).isEqualTo(
                "event=INVALID_TOKEN ip=\"1.1.1.1\" method=\"GET\" path=\"/api/v1/players/me\"");
    }

    @Test
    void registrationIsLoggedAsInfo() {
        SecurityAudit.playerRegistered("1.2.3.4", "alice");

        assertThat(appender.list.get(0).getLevel()).isEqualTo(Level.INFO);
        assertThat(onlyMessage())
                .isEqualTo("event=PLAYER_REGISTERED ip=\"1.2.3.4\" username=\"alice\"");
    }

    @Test
    void failedPasswordChangeIsAWarningAndNamesThePlayerById() {
        UUID id = UUID.randomUUID();

        SecurityAudit.passwordChangeFailed(id);

        assertThat(appender.list.get(0).getLevel()).isEqualTo(Level.WARN);
        assertThat(onlyMessage())
                .isEqualTo("event=PASSWORD_CHANGE_FAILED player=\"" + id + "\"");
    }

    @Test
    void adminActionNamesWhoDidWhatOnWhich() {
        UUID admin = UUID.randomUUID();
        UUID game = UUID.randomUUID();

        SecurityAudit.adminAction(admin, "ROLL_GAME", game);

        assertThat(onlyMessage()).isEqualTo(
                "event=ADMIN_ACTION player=\"" + admin + "\" action=\"ROLL_GAME\" game=\"" + game + "\"");
    }

    @Test
    void webSocketDestinationFromAClientIsCleaned() {
        SecurityAudit.websocketRejected("SEND", "/topic/x\nevent=FAKE", "not allowed");

        assertThat(onlyMessage()).doesNotContain("\n");
    }

    @Test
    void blockedPasswordChangeIsAWarning() {
        UUID id = UUID.randomUUID();

        SecurityAudit.passwordChangeBlocked(id);

        assertThat(appender.list.get(0).getLevel()).isEqualTo(Level.WARN);
        assertThat(onlyMessage())
                .isEqualTo("event=PASSWORD_CHANGE_BLOCKED player=\"" + id + "\"");
    }
}