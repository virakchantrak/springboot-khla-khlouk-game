package kh.virakchantrak.KhlaKhlouk.websocket;

import io.jsonwebtoken.JwtException;
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
            case SEND -> throw new MessageDeliveryException(
                    "Clients are not allowed to send messages");
            default -> {
                // DISCONNECT, UNSUBSCRIBE, ACK... nothing to check
            }
        }

        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String authorization = accessor.getFirstNativeHeader("Authorization");

        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new MessageDeliveryException("Missing WebSocket authorization");
        }

        String token = authorization.substring(7);

        try {
            String username = jwtService.extractUsername(token);

            PlayerPrincipal player = (PlayerPrincipal) playerDetailsService
                    .loadUserByUsername(username);

            if (!jwtService.isTokenValid(token, player)) {
                throw new MessageDeliveryException("Invalid or expired token");
            }

            accessor.setUser(new UsernamePasswordAuthenticationToken(
                    player, null, player.getAuthorities()));

        } catch (JwtException | IllegalArgumentException | UsernameNotFoundException e) {
            throw new MessageDeliveryException("Invalid or expired token");
        }
    }

    private void checkSubscription(StompHeaderAccessor accessor) {
        if (accessor.getUser() == null) {
            throw new MessageDeliveryException("Not authenticated");
        }

        String destination = accessor.getDestination();

        if (destination == null || !GAME_TOPIC.matcher(destination).matches()) {
            throw new MessageDeliveryException("Subscription not allowed");
        }
    }
}