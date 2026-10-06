package kh.virakchantrak.KhlaKhlouk.game.service;

import kh.virakchantrak.KhlaKhlouk.game.controller.dto.GameDetailsResponse;
import kh.virakchantrak.KhlaKhlouk.game.domain.Game;
import kh.virakchantrak.KhlaKhlouk.game.domain.GameResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface GameService {

    Game createGame();

    Game startBetting(UUID gameId);

    GameResult rollGame(UUID gameId);

    GameResult getGameResult(UUID gameId);

    Page<Game> getGames(Pageable pageable);

    GameDetailsResponse getGameDetails(UUID gameId);
}
