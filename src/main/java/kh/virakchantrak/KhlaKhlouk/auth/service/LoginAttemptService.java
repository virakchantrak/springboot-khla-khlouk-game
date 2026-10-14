package kh.virakchantrak.KhlaKhlouk.auth.service;

import kh.virakchantrak.KhlaKhlouk.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LoginAttemptService {

    static final int MAX_ATTEMPTS_PER_ACCOUNT_AND_IP = 5;
    static final int MAX_ATTEMPTS_PER_IP = 20;
    static final Duration WINDOW = Duration.ofMinutes(15);

    private static final int PURGE_THRESHOLD = 10_000;
    private static final int MAX_USERNAME_KEY_LENGTH = 100;

    static final int MAX_PASSWORD_CHANGE_ATTEMPTS = 5;

    private record Window(int count, Instant start) {
    }

    private final Clock clock;
    private final ConcurrentHashMap<String, Window> attempts = new ConcurrentHashMap<>();

    public LoginAttemptService() {
        this(Clock.systemUTC());
    }

    // for tests, so they can move time forward
    LoginAttemptService(Clock clock) {
        this.clock = clock;
    }

    /**
     * Counts this attempt BEFORE the password is checked, so parallel guesses
     * cannot slip through while earlier ones are still being verified.
     */
    public void registerAttempt(String ip, String username) {
        int accountAttempts = increment(accountKey(ip, username));
        int ipAttempts = increment(ipKey(ip));

        purgeExpiredIfLarge();

        if (accountAttempts > MAX_ATTEMPTS_PER_ACCOUNT_AND_IP
                || ipAttempts > MAX_ATTEMPTS_PER_IP) {
            throw new BusinessException(
                    "TOO_MANY_LOGIN_ATTEMPTS",
                    "Too many login attempts. Please try again later.",
                    HttpStatus.TOO_MANY_REQUESTS
            );
        }
    }

    /** A correct login clears this account's counter and gives the IP its attempt back. */
    public void recordSuccess(String ip, String username) {
        attempts.remove(accountKey(ip, username));
        attempts.computeIfPresent(ipKey(ip), (key, window) ->
                window.count() <= 1 ? null : new Window(window.count() - 1, window.start()));
    }

    /** Counts a password-change attempt for this player; throws 429 when over the limit. */
    public void registerPasswordChangeAttempt(UUID playerId) {
        int count = increment("pwchange:" + playerId);

        purgeExpiredIfLarge();

        if (count > MAX_PASSWORD_CHANGE_ATTEMPTS) {
            throw new BusinessException(
                    "TOO_MANY_PASSWORD_ATTEMPTS",
                    "Too many password change attempts. Please try again later.",
                    HttpStatus.TOO_MANY_REQUESTS
            );
        }
    }

    public void recordPasswordChangeSuccess(UUID playerId) {
        attempts.remove("pwchange:" + playerId);
    }

    private int increment(String key) {
        Instant now = clock.instant();

        return attempts.merge(
                key,
                new Window(1, now),
                (old, fresh) -> isExpired(old, now)
                        ? fresh
                        : new Window(old.count() + 1, old.start())
        ).count();
    }

    private boolean isExpired(Window window, Instant now) {
        return !now.isBefore(window.start().plus(WINDOW));
    }

    private void purgeExpiredIfLarge() {
        if (attempts.size() > PURGE_THRESHOLD) {
            Instant now = clock.instant();
            attempts.entrySet().removeIf(entry -> isExpired(entry.getValue(), now));
        }
    }

    private String accountKey(String ip, String username) {
        String normalized = username == null
                ? ""
                : username.trim().toLowerCase(Locale.ROOT);

        if (normalized.length() > MAX_USERNAME_KEY_LENGTH) {
            normalized = normalized.substring(0, MAX_USERNAME_KEY_LENGTH);
        }

        return "account:" + ip + "|" + normalized;
    }

    private String ipKey(String ip) {
        return "ip:" + ip;
    }
}