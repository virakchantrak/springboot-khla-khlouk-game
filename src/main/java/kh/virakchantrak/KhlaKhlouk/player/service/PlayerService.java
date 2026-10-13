package kh.virakchantrak.KhlaKhlouk.player.service;

import kh.virakchantrak.KhlaKhlouk.player.domain.Player;

import java.util.UUID;

public interface PlayerService {

    Player createPlayer(
            String username,
            String password
    );

    Player getPlayer(UUID playerId);
}
