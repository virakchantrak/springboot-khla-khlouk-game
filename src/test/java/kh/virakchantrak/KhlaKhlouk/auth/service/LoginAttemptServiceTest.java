package kh.virakchantrak.KhlaKhlouk.auth.service;

import kh.virakchantrak.KhlaKhlouk.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginAttemptServiceTest {

    private static class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private final MutableClock clock = new MutableClock();
    private final LoginAttemptService service = new LoginAttemptService(clock);

    private void attempts(String ip, String username, int times) {
        for (int i = 0; i < times; i++) {
            service.registerAttempt(ip, username);
        }
    }

    @Test
    void allowsFiveAttemptsThenBlocksTheSixth() {
        assertThatCode(() -> attempts("1.1.1.1", "alice", 5)).doesNotThrowAnyException();

        assertThatThrownBy(() -> service.registerAttempt("1.1.1.1", "alice"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "TOO_MANY_LOGIN_ATTEMPTS")
                .hasFieldOrPropertyWithValue("status", HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void successfulLoginResetsTheCounter() {
        attempts("1.1.1.1", "alice", 4);
        service.recordSuccess("1.1.1.1", "alice");

        assertThatCode(() -> attempts("1.1.1.1", "alice", 4)).doesNotThrowAnyException();
    }

    @Test
    void blockLastsForTheWholeWindowThenExpires() {
        attempts("1.1.1.1", "alice", 5);
        assertThatThrownBy(() -> service.registerAttempt("1.1.1.1", "alice"))
                .isInstanceOf(BusinessException.class);

        clock.advance(Duration.ofMinutes(14).plusSeconds(59));
        assertThatThrownBy(() -> service.registerAttempt("1.1.1.1", "alice"))
                .isInstanceOf(BusinessException.class);

        clock.advance(Duration.ofSeconds(1));
        assertThatCode(() -> service.registerAttempt("1.1.1.1", "alice"))
                .doesNotThrowAnyException();
    }

    @Test
    void attackerCannotLockTheRealUserOut() {
        attempts("6.6.6.6", "alice", 5);
        assertThatThrownBy(() -> service.registerAttempt("6.6.6.6", "alice"))
                .isInstanceOf(BusinessException.class);

        // alice herself, from another address, is not affected
        assertThatCode(() -> service.registerAttempt("2.2.2.2", "alice"))
                .doesNotThrowAnyException();
    }

    @Test
    void oneIpTryingManyUsernamesGetsBlocked() {
        for (int i = 0; i < 20; i++) {
            service.registerAttempt("6.6.6.6", "user" + i);
        }

        assertThatThrownBy(() -> service.registerAttempt("6.6.6.6", "brand-new-user"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void usernameCaseAndSpacesDoNotBypassTheLimit() {
        attempts("1.1.1.1", "Alice", 5);

        assertThatThrownBy(() -> service.registerAttempt("1.1.1.1", "  ALICE "))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void differentUsersFromTheSameIpAreCountedSeparately() {
        attempts("1.1.1.1", "alice", 5);

        assertThatCode(() -> service.registerAttempt("1.1.1.1", "bob"))
                .doesNotThrowAnyException();
    }

    @Test
    void passwordChangeAllowsFiveAttemptsThenBlocks() {
        UUID player = UUID.randomUUID();

        for (int i = 0; i < 5; i++) {
            service.registerPasswordChangeAttempt(player);
        }

        assertThatThrownBy(() -> service.registerPasswordChangeAttempt(player))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "TOO_MANY_PASSWORD_ATTEMPTS")
                .hasFieldOrPropertyWithValue("status", HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void passwordChangeSuccessResetsTheCounter() {
        UUID player = UUID.randomUUID();

        for (int i = 0; i < 4; i++) {
            service.registerPasswordChangeAttempt(player);
        }
        service.recordPasswordChangeSuccess(player);

        assertThatCode(() -> {
            for (int i = 0; i < 4; i++) {
                service.registerPasswordChangeAttempt(player);
            }
        }).doesNotThrowAnyException();
    }

    @Test
    void passwordChangeLimitIsPerPlayerAndExpires() {
        UUID attacked = UUID.randomUUID();
        UUID other = UUID.randomUUID();

        for (int i = 0; i < 5; i++) {
            service.registerPasswordChangeAttempt(attacked);
        }

        assertThatThrownBy(() -> service.registerPasswordChangeAttempt(attacked))
                .isInstanceOf(BusinessException.class);
        assertThatCode(() -> service.registerPasswordChangeAttempt(other))
                .doesNotThrowAnyException();

        clock.advance(Duration.ofMinutes(15));
        assertThatCode(() -> service.registerPasswordChangeAttempt(attacked))
                .doesNotThrowAnyException();
    }

    @Test
    void loginAttemptsDoNotUseUpThePasswordChangeBudget() {
        UUID player = UUID.randomUUID();
        attempts("1.1.1.1", "alice", 5);

        assertThatCode(() -> service.registerPasswordChangeAttempt(player))
                .doesNotThrowAnyException();
    }

    @Test
    void registrationsAreBlockedAfterTheConfiguredLimit() {
        LoginAttemptService strict = new LoginAttemptService(clock, 3);

        for (int i = 0; i < 3; i++) {
            strict.registerRegistrationAttempt("1.1.1.1");
        }

        assertThatThrownBy(() -> strict.registerRegistrationAttempt("1.1.1.1"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "TOO_MANY_REGISTRATIONS")
                .hasFieldOrPropertyWithValue("status", HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void registrationLimitIsPerAddress() {
        LoginAttemptService strict = new LoginAttemptService(clock, 3);

        for (int i = 0; i < 3; i++) {
            strict.registerRegistrationAttempt("6.6.6.6");
        }

        assertThatThrownBy(() -> strict.registerRegistrationAttempt("6.6.6.6"))
                .isInstanceOf(BusinessException.class);
        assertThatCode(() -> strict.registerRegistrationAttempt("2.2.2.2"))
                .doesNotThrowAnyException();
    }

    @Test
    void registrationWindowIsOneHourNotFifteenMinutes() {
        LoginAttemptService strict = new LoginAttemptService(clock, 3);

        for (int i = 0; i < 4; i++) {
            try {
                strict.registerRegistrationAttempt("1.1.1.1");
            } catch (BusinessException ignored) {
                // the 4th attempt is the one that gets blocked
            }
        }

        // the login window would be over by now, the registration window is not
        clock.advance(Duration.ofMinutes(59).plusSeconds(59));
        assertThatThrownBy(() -> strict.registerRegistrationAttempt("1.1.1.1"))
                .isInstanceOf(BusinessException.class);

        clock.advance(Duration.ofSeconds(1));
        assertThatCode(() -> strict.registerRegistrationAttempt("1.1.1.1"))
                .doesNotThrowAnyException();
    }

    @Test
    void loginAttemptsDoNotUseUpTheRegistrationBudget() {
        LoginAttemptService strict = new LoginAttemptService(clock, 3);

        for (int i = 0; i < 5; i++) {
            strict.registerAttempt("1.1.1.1", "alice");
        }

        assertThatCode(() -> strict.registerRegistrationAttempt("1.1.1.1"))
                .doesNotThrowAnyException();
    }
}