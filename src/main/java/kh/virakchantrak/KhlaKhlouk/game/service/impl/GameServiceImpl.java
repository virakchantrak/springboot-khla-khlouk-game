package kh.virakchantrak.KhlaKhlouk.game.service.impl;

import kh.virakchantrak.KhlaKhlouk.common.constant.GameStatus;
import kh.virakchantrak.KhlaKhlouk.game.domain.Game;
import kh.virakchantrak.KhlaKhlouk.game.repository.GameRepository;
import kh.virakchantrak.KhlaKhlouk.game.repository.GameResultRepository;
import kh.virakchantrak.KhlaKhlouk.game.service.GameService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class GameServiceImpl implements GameService {

    private final GameRepository gameRepository;
    private final GameResultRepository gameResultRepository;

    @Override
    public Game createGame() {
        Game game = new Game();
        game.setStatus(GameStatus.WAITING);

        return gameRepository.save(game);
    }

    @Override
    public Game startBetting(UUID gameId) {
        Game game = getGame(gameId);

        if (game.getStatus() != GameStatus.WAITING) {
            throw new IllegalStateException(
                    "Game is not waiting"
            );
        }

        game.setStatus(GameStatus.BETTING);
        game.setStartedAt(Instant.now());

        return game;
    }

    @Override
    public Game rollGame(UUID gameId) {
        Game game = getGame(gameId);

        if (game.getStatus() != GameStatus.BETTING) {
            throw new IllegalStateException(
                    "Game is not accepting bets"
            );
        }

        game.setStatus(GameStatus.ROLLING);

        // RollResult + payout processing will come here.

        return game;
    }

    private Game getGame(UUID gameId) {
        return gameRepository.findById(gameId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Game not found"
                        ));
    }
}
