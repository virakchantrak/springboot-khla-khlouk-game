package kh.virakchantrak.KhlaKhlouk.game.service;

import kh.virakchantrak.KhlaKhlouk.game.domain.Game;

import java.util.UUID;

public interface GameService {

    Game createGame();

    Game startBetting(UUID gameId);

    Game rollGame(UUID gameId);
}
