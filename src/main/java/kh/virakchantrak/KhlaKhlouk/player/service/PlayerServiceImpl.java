package kh.virakchantrak.KhlaKhlouk.player.service;

import kh.virakchantrak.KhlaKhlouk.player.domain.Player;
import kh.virakchantrak.KhlaKhlouk.player.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class PlayerServiceImpl implements PlayerService {

    private final PlayerRepository playerRepository;

    @Override
    public Player createPlayer(
            String username,
            BigDecimal initialBalance
    ) {
        if (playerRepository.findByUsername(username).isPresent()) {
            throw new IllegalArgumentException(
                    "Username already exists"
            );
        }

        if (initialBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(
                    "Initial balance cannot be negative"
            );
        }

        Player player = new Player();

        player.setUsername(username);
        player.setBalance(initialBalance);

        return playerRepository.save(player);
    }

    @Override
    @Transactional(readOnly = true)
    public Player getPlayer(UUID playerId) {
        return playerRepository.findById(playerId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Player not found"
                        ));
    }
}
