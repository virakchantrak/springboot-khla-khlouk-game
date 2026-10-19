package kh.virakchantrak.KhlaKhlouk.auth.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * One place for security-relevant log lines.
 * There is deliberately no parameter for passwords or tokens.
 */
public final class SecurityAudit {

    private static final Logger log = LoggerFactory.getLogger("SECURITY_AUDIT");
    private static final int MAX_LENGTH = 100;

    private SecurityAudit() {
    }

    public static void loginSucceeded(String ip, String username) {
        log.info("event=LOGIN_SUCCESS ip=\"{}\" username=\"{}\"",
                clean(ip), clean(username));
    }

    public static void loginFailed(String ip, String username) {
        log.warn("event=LOGIN_FAILED ip=\"{}\" username=\"{}\"",
                clean(ip), clean(username));
    }

    public static void loginBlocked(String ip, String username) {
        log.warn("event=LOGIN_BLOCKED ip=\"{}\" username=\"{}\"",
                clean(ip), clean(username));
    }

    public static void accessDenied(String ip, String username, String method, String path) {
        log.warn("event=ACCESS_DENIED ip=\"{}\" username=\"{}\" method=\"{}\" path=\"{}\"",
                clean(ip), clean(username), clean(method), clean(path));
    }

    public static void invalidToken(String ip, String method, String path) {
        log.warn("event=INVALID_TOKEN ip=\"{}\" method=\"{}\" path=\"{}\"",
                clean(ip), clean(method), clean(path));
    }

    public static void playerRegistered(String ip, String username) {
        log.info("event=PLAYER_REGISTERED ip=\"{}\" username=\"{}\"",
                clean(ip), clean(username));
    }

    public static void passwordChanged(UUID playerId) {
        log.info("event=PASSWORD_CHANGED player=\"{}\"", clean(playerId));
    }

    public static void passwordChangeFailed(UUID playerId) {
        log.warn("event=PASSWORD_CHANGE_FAILED player=\"{}\"", clean(playerId));
    }

    public static void adminAction(UUID adminId, String action, UUID gameId) {
        log.info("event=ADMIN_ACTION player=\"{}\" action=\"{}\" game=\"{}\"",
                clean(adminId), clean(action), clean(gameId));
    }

    public static void websocketRejected(String command, String destination, String reason) {
        log.warn("event=WEBSOCKET_REJECTED command=\"{}\" destination=\"{}\" reason=\"{}\"",
                clean(command), clean(destination), clean(reason));
    }

    public static void passwordChangeBlocked(UUID playerId) {
        log.warn("event=PASSWORD_CHANGE_BLOCKED player=\"{}\"", clean(playerId));
    }

    static String clean(UUID id) {
        return clean(id == null ? null : id.toString());
    }

    // stops line breaks and quotes in user input from forging extra log lines
    static String clean(String value) {
        if (value == null) {
            return "-";
        }

        String cleaned = value.replaceAll("[\\p{Cntrl}\"]", "_");

        return cleaned.length() > MAX_LENGTH
                ? cleaned.substring(0, MAX_LENGTH) + "..."
                : cleaned;
    }
}