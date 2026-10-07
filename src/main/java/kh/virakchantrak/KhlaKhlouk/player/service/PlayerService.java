package kh.virakchantrak.KhlaKhlouk.player.service;

import kh.virakchantrak.KhlaKhlouk.player.domain.Player;

import java.math.BigDecimal;
import java.util.UUID;

public interface PlayerService {

    Player createPlayer(
            String username,
            String password,
            BigDecimal initialBalance
    );

    Player getPlayer(UUID playerId);
}
