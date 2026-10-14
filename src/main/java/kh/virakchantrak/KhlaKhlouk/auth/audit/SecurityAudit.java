package kh.virakchantrak.KhlaKhlouk.auth.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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