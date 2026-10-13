package kh.virakchantrak.KhlaKhlouk.player.service;

import kh.virakchantrak.KhlaKhlouk.common.exception.BusinessException;
import kh.virakchantrak.KhlaKhlouk.player.domain.Player;
import kh.virakchantrak.KhlaKhlouk.player.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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
    @Value("${app.player.starting-balance:1000.00}")
    private BigDecimal startingBalance;

    @Override
    public Player createPlayer(
            String username,
            String password
    ) {
        if (playerRepository.findByUsername(username).isPresent()) {
            throw new BusinessException(
                    "USERNAME_ALREADY_EXISTS",
                    "Username already exists",
                    HttpStatus.CONFLICT
            );
        }

        Player player = new Player();

        player.setUsername(username);
        player.setPasswordHash(passwordEncoder.encode(password));
        player.setBalance(startingBalance);

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
