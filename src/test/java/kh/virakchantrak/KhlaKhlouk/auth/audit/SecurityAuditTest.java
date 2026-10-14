package kh.virakchantrak.KhlaKhlouk.auth.audit;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

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
}