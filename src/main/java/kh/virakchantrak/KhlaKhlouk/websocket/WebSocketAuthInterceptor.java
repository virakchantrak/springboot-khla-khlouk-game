package kh.virakchantrak.KhlaKhlouk.websocket;

import kh.virakchantrak.KhlaKhlouk.auth.security.PlayerPrincipal;
import kh.virakchantrak.KhlaKhlouk.auth.service.JwtService;
import kh.virakchantrak.KhlaKhlouk.auth.service.PlayerDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;
    private final PlayerDetailsService playerDetailsService;

    @Override
    public Message<?> preSend(
            Message<?> message,
            MessageChannel channel
    ) {
        StompHeaderAccessor accessor =
                StompHeaderAccessor.wrap(message);

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {

            String authorization =
                    accessor.getFirstNativeHeader("Authorization");

            if (authorization == null
                    || !authorization.startsWith("Bearer ")) {
                throw new IllegalArgumentException(
                        "Missing WebSocket authorization"
                );
            }

            String token = authorization.substring(7);

            String username = jwtService.extractUsername(token);

            PlayerPrincipal player =
                    (PlayerPrincipal) playerDetailsService
                            .loadUserByUsername(username);

            if (!jwtService.isTokenValid(token, player)) {
                throw new IllegalArgumentException(
                        "Invalid or expired token"
                );
            }

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            player,
                            null,
                            player.getAuthorities()
                    );

            accessor.setUser(authentication);
        }

        return message;
    }
}
