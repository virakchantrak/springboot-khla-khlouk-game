package kh.virakchantrak.KhlaKhlouk.websocket;

import io.jsonwebtoken.JwtException;
import kh.virakchantrak.KhlaKhlouk.auth.security.PlayerPrincipal;
import kh.virakchantrak.KhlaKhlouk.auth.service.JwtService;
import kh.virakchantrak.KhlaKhlouk.auth.service.PlayerDetailsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebSocketAuthInterceptorTest {

    @Mock JwtService jwtService;
    @Mock PlayerDetailsService playerDetailsService;

    private final MessageChannel channel = mock(MessageChannel.class);
    private final String gameTopic = "/topic/games/" + UUID.randomUUID();

    private WebSocketAuthInterceptor interceptor() {
        return new WebSocketAuthInterceptor(jwtService, playerDetailsService);
    }

    private Principal loggedInUser() {
        return new UsernamePasswordAuthenticationToken("alice", null, List.of());
    }

    private Message<?> stompMessage(
            StompCommand command,
            String destination,
            Principal user,
            String authorization
    ) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        if (destination != null) accessor.setDestination(destination);
        if (user != null) accessor.setUser(user);
        if (authorization != null) accessor.addNativeHeader("Authorization", authorization);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    // ---------- the forgery hole ----------

    @Test
    void clientCannotSendMessagesToBrokerTopics() {
        Message<?> forged = stompMessage(StompCommand.SEND, gameTopic, loggedInUser(), null);

        assertThatThrownBy(() -> interceptor().preSend(forged, channel))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void clientCannotSubscribeToWildcardTopics() {
        Message<?> wildcard = stompMessage(
                StompCommand.SUBSCRIBE, "/topic/**", loggedInUser(), null);

        assertThatThrownBy(() -> interceptor().preSend(wildcard, channel))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void clientCannotSubscribeWithoutBeingLoggedIn() {
        Message<?> anonymous = stompMessage(StompCommand.SUBSCRIBE, gameTopic, null, null);

        assertThatThrownBy(() -> interceptor().preSend(anonymous, channel))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void loggedInClientCanSubscribeToOneGameTopic() {
        Message<?> subscribe = stompMessage(
                StompCommand.SUBSCRIBE, gameTopic, loggedInUser(), null);

        assertThat(interceptor().preSend(subscribe, channel)).isSameAs(subscribe);
    }

    // ---------- connecting ----------

    @Test
    void connectWithoutTokenIsRejected() {
        Message<?> connect = stompMessage(StompCommand.CONNECT, null, null, null);

        assertThatThrownBy(() -> interceptor().preSend(connect, channel))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void connectWithBadTokenIsRejected() {
        when(jwtService.extractUsername("bad")).thenThrow(new JwtException("bad token"));

        Message<?> connect = stompMessage(
                StompCommand.CONNECT, null, null, "Bearer bad");

        assertThatThrownBy(() -> interceptor().preSend(connect, channel))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void connectWithValidTokenAttachesThePlayerToTheSession() {
        PlayerPrincipal player = new PlayerPrincipal(
                UUID.randomUUID(), "alice", "hash",
                List.of(new SimpleGrantedAuthority("ROLE_PLAYER")));

        when(jwtService.extractUsername("good")).thenReturn("alice");
        when(playerDetailsService.loadUserByUsername("alice")).thenReturn(player);
        when(jwtService.isTokenValid("good", player)).thenReturn(true);

        Message<?> connect = stompMessage(
                StompCommand.CONNECT, null, null, "Bearer good");

        interceptor().preSend(connect, channel);

        StompHeaderAccessor after =
                MessageHeaderAccessor.getAccessor(connect, StompHeaderAccessor.class);
        assertThat(after.getUser()).isNotNull();
    }
}