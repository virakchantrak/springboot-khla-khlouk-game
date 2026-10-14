package kh.virakchantrak.KhlaKhlouk.auth.service;

import kh.virakchantrak.KhlaKhlouk.auth.controller.dto.ChangePasswordRequest;
import kh.virakchantrak.KhlaKhlouk.common.exception.BusinessException;
import kh.virakchantrak.KhlaKhlouk.player.domain.Player;
import kh.virakchantrak.KhlaKhlouk.player.repository.PlayerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock PlayerRepository playerRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock CurrentPlayerService currentPlayerService;

    @InjectMocks AuthService authService;

    private final UUID playerId = UUID.randomUUID();

    private ChangePasswordRequest request(String current, String next) {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword(current);
        request.setNewPassword(next);
        return request;
    }

    private Player playerWithHash(String hash) {
        Player player = new Player();
        player.setPasswordHash(hash);
        return player;
    }

    @Test
    void changingPasswordReplacesHashAndEndsExistingSessions() {
        Player player = playerWithHash("old-hash");

        when(currentPlayerService.getPlayerId()).thenReturn(playerId);
        when(playerRepository.findById(playerId)).thenReturn(Optional.of(player));
        when(passwordEncoder.matches("old-pass", "old-hash")).thenReturn(true);
        when(passwordEncoder.encode("new-pass-1")).thenReturn("new-hash");

        authService.changePassword(request("old-pass", "new-pass-1"));

        assertThat(player.getPasswordHash()).isEqualTo("new-hash");
        assertThat(player.getTokenVersion()).isEqualTo(1);
    }

    @Test
    void wrongCurrentPasswordChangesNothing() {
        Player player = playerWithHash("old-hash");

        when(currentPlayerService.getPlayerId()).thenReturn(playerId);
        when(playerRepository.findById(playerId)).thenReturn(Optional.of(player));
        when(passwordEncoder.matches("wrong", "old-hash")).thenReturn(false);

        assertThatThrownBy(() ->
                authService.changePassword(request("wrong", "new-pass-1")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_CURRENT_PASSWORD");

        assertThat(player.getPasswordHash()).isEqualTo("old-hash");
        assertThat(player.getTokenVersion()).isZero();
    }
}