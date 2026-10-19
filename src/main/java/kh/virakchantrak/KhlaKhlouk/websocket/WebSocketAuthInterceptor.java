package kh.virakchantrak.KhlaKhlouk.websocket;

import io.jsonwebtoken.JwtException;
import kh.virakchantrak.KhlaKhlouk.auth.audit.SecurityAudit;
import kh.virakchantrak.KhlaKhlouk.auth.security.PlayerPrincipal;
import kh.virakchantrak.KhlaKhlouk.auth.service.JwtService;
import kh.virakchantrak.KhlaKhlouk.auth.service.PlayerDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    // exactly one game: /topic/games/<uuid>  (no wildcards)
    private static final Pattern GAME_TOPIC = Pattern.compile(
            "^/topic/games/[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}$");

    private final JwtService jwtService;
    private final PlayerDetailsService playerDetailsService;

    @Override
    public Message<?> preSend(
            Message<?> message,
            MessageChannel channel
    ) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        // heartbeats and internal messages have no STOMP command
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        switch (accessor.getCommand()) {
            case CONNECT -> authenticate(accessor);
            case SUBSCRIBE -> checkSubscription(accessor);
            case SEND -> throw reject(accessor, "Clients are not allowed to send messages");
            default -> {
                // DISCONNECT, UNSUBSCRIBE, ACK... nothing to check
            }
        }

        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String authorization = accessor.getFirstNativeHeader("Authorization");

        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw reject(accessor, "Missing WebSocket authorization");
        }

        String token = authorization.substring(7);

        try {
            String username = jwtService.extractUsername(token);

            PlayerPrincipal player = (PlayerPrincipal) playerDetailsService
                    .loadUserByUsername(username);

            if (!jwtService.isTokenValid(token, player)) {
                throw reject(accessor, "Invalid or expired token");
            }

            accessor.setUser(new UsernamePasswordAuthenticationToken(
                    player, null, player.getAuthorities()));

        } catch (JwtException | IllegalArgumentException | UsernameNotFoundException e) {
            throw reject(accessor, "Invalid or expired token");
        }
    }

    private void checkSubscription(StompHeaderAccessor accessor) {
        if (accessor.getUser() == null) {
            throw reject(accessor, "Not authenticated");
        }

        String destination = accessor.getDestination();

        if (destination == null || !GAME_TOPIC.matcher(destination).matches()) {
            throw reject(accessor, "Subscription not allowed");
        }
    }

    // logs the rejection, then gives back the exception to throw
    private MessageDeliveryException reject(StompHeaderAccessor accessor, String reason) {
        SecurityAudit.websocketRejected(
                accessor.getCommand() != null ? accessor.getCommand().name() : null,
                accessor.getDestination(),
                reason
        );
        return new MessageDeliveryException(reason);
    }
}