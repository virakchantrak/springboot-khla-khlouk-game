package kh.virakchantrak.KhlaKhlouk.auth.service;

import kh.virakchantrak.KhlaKhlouk.auth.dto.ChangePasswordRequest;
import kh.virakchantrak.KhlaKhlouk.common.exception.BusinessException;
import kh.virakchantrak.KhlaKhlouk.player.domain.Player;
import kh.virakchantrak.KhlaKhlouk.player.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final PlayerRepository playerRepository;
    private final PasswordEncoder passwordEncoder;
    private final CurrentPlayerService currentPlayerService;

    @Transactional
    public void changePassword(
            ChangePasswordRequest request
    ) {
        UUID playerId = currentPlayerService.getPlayerId();
        Player player = playerRepository.findById(playerId)
                .orElseThrow(() -> new BusinessException(
                        "PLAYER_NOT_FOUND",
                        "Player not found",
                        HttpStatus.NOT_FOUND
                ));

        if (!passwordEncoder.matches(
                request.getCurrentPassword(),
                player.getPasswordHash()
        )) {
            throw new BusinessException(
                    "INVALID_CURRENT_PASSWORD",
                    "Current password is incorrect",
                    HttpStatus.BAD_REQUEST
            );
        }

        player.setPasswordHash(
                passwordEncoder.encode(request.getNewPassword())
        );
    }
}
