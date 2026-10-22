package kh.virakchantrak.KhlaKhlouk.auth.service;

import kh.virakchantrak.KhlaKhlouk.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
    static final int MAX_PASSWORD_CHANGE_ATTEMPTS = 5;
    static final int DEFAULT_MAX_REGISTRATIONS_PER_IP_PER_HOUR = 5;

    static final Duration WINDOW = Duration.ofMinutes(15);
    static final Duration REGISTRATION_WINDOW = Duration.ofHours(1);

    private static final int PURGE_THRESHOLD = 10_000;
    private static final int MAX_USERNAME_KEY_LENGTH = 100;

    private record Window(int count, Instant expiresAt) {
    }

    private final Clock clock;
    private final int maxRegistrationsPerIpPerHour;
    private final ConcurrentHashMap<String, Window> attempts = new ConcurrentHashMap<>();

    @Autowired
    public LoginAttemptService(
            @Value("${app.registration.max-per-ip-per-hour}") int maxRegistrationsPerIpPerHour
    ) {
        this(Clock.systemUTC(), maxRegistrationsPerIpPerHour);
    }

    // for tests, so they can move time forward
    LoginAttemptService(Clock clock) {
        this(clock, DEFAULT_MAX_REGISTRATIONS_PER_IP_PER_HOUR);
    }

    LoginAttemptService(Clock clock, int maxRegistrationsPerIpPerHour) {
        this.clock = clock;
        this.maxRegistrationsPerIpPerHour = maxRegistrationsPerIpPerHour;
    }

    /**
     * Counts this attempt BEFORE the password is checked, so parallel guesses
     * cannot slip through while earlier ones are still being verified.
     */
    public void registerAttempt(String ip, String username) {
        int accountAttempts = increment(accountKey(ip, username), WINDOW);
        int ipAttempts = increment(ipKey(ip), WINDOW);

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
                window.count() <= 1 ? null : new Window(window.count() - 1, window.expiresAt()));
    }

    /** Counts a password-change attempt for this player; throws 429 when over the limit. */
    public void registerPasswordChangeAttempt(UUID playerId) {
        int count = increment("pwchange:" + playerId, WINDOW);

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

    /** Counts a registration from this address; throws 429 when over the hourly limit. */
    public void registerRegistrationAttempt(String ip) {
        int count = increment("register:" + ip, REGISTRATION_WINDOW);

        purgeExpiredIfLarge();

        if (count > maxRegistrationsPerIpPerHour) {
            throw new BusinessException(
                    "TOO_MANY_REGISTRATIONS",
                    "Too many accounts were created from this address. Please try again later.",
                    HttpStatus.TOO_MANY_REQUESTS
            );
        }
    }

    private int increment(String key, Duration duration) {
        Instant now = clock.instant();

        return attempts.merge(
                key,
                new Window(1, now.plus(duration)),
                (old, fresh) -> isExpired(old, now)
                        ? fresh
                        : new Window(old.count() + 1, old.expiresAt())
        ).count();
    }

    private boolean isExpired(Window window, Instant now) {
        return !now.isBefore(window.expiresAt());
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