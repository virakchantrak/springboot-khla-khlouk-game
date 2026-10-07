package kh.virakchantrak.KhlaKhlouk.player.service;

import kh.virakchantrak.KhlaKhlouk.common.exception.BusinessException;
import kh.virakchantrak.KhlaKhlouk.player.domain.Player;
import kh.virakchantrak.KhlaKhlouk.player.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class PlayerServiceImpl implements PlayerService {

    private final PlayerRepository playerRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public Player createPlayer(
            String username,
            String password,
            BigDecimal initialBalance
    ) {
        if (playerRepository.findByUsername(username).isPresent()) {
            throw new BusinessException(
                    "USERNAME_ALREADY_EXISTS",
                    "Username already exists",
                    HttpStatus.CONFLICT
            );
        }

        if (initialBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(
                    "INITIAL_BALANCE_NEGATIVE",
                    "Initial balance cannot be negative",
                    HttpStatus.BAD_REQUEST
            );
        }

        Player player = new Player();

        player.setUsername(username);
        player.setPasswordHash(passwordEncoder.encode(password));
        player.setBalance(initialBalance);

        return playerRepository.save(player);
    }

    @Override
    @Transactional(readOnly = true)
    public Player getPlayer(UUID playerId) {
        return playerRepository.findById(playerId)
                .orElseThrow(() ->
                        new BusinessException(
                                "PLAYER_NOT_FOUND",
                                "Player not found",
                                HttpStatus.NOT_FOUND
                        ));
    }
}
