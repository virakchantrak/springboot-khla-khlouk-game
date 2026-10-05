package kh.virakchantrak.KhlaKhlouk.game.service;

import kh.virakchantrak.KhlaKhlouk.bet.domain.Bet;
import kh.virakchantrak.KhlaKhlouk.bet.repository.BetRepository;
import kh.virakchantrak.KhlaKhlouk.game.domain.Dice;
import kh.virakchantrak.KhlaKhlouk.game.domain.Game;
import kh.virakchantrak.KhlaKhlouk.game.domain.GameResult;
import kh.virakchantrak.KhlaKhlouk.game.domain.GameStatus;
import kh.virakchantrak.KhlaKhlouk.game.domain.PayoutCalculator;
import kh.virakchantrak.KhlaKhlouk.game.domain.RollResult;
import kh.virakchantrak.KhlaKhlouk.game.repository.GameRepository;
import kh.virakchantrak.KhlaKhlouk.game.repository.GameResultRepository;
import kh.virakchantrak.KhlaKhlouk.player.domain.Player;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class GameServiceImpl implements GameService {

    private final GameRepository gameRepository;
    private final BetRepository betRepository;
    private final GameResultRepository gameResultRepository;
    private final Dice dice;
    private final PayoutCalculator payoutCalculator;

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

        // 1. Roll the dice
        RollResult result = dice.rollThree();

        // 2. Save the game result
        GameResult gameResult = new GameResult();

        gameResult.setGame(game);
        gameResult.setDice1(result.dice1());
        gameResult.setDice2(result.dice2());
        gameResult.setDice3(result.dice3());

        gameResultRepository.save(gameResult);

        // 3. Process every bet
        List<Bet> bets = betRepository.findByGameId(gameId);

        for (Bet bet : bets) {

            long matches = result.count(bet.getSymbol());

            BigDecimal payout = payoutCalculator.calculate(
                    bet.getAmount(),
                    matches
            );

            bet.setPayout(payout);

            // 4. Pay the winner
            if (payout.compareTo(BigDecimal.ZERO) > 0) {

                Player player = bet.getPlayer();

                player.setBalance(
                        player.getBalance().add(payout)
                );
            }
        }

        // 5. Finish the game
        game.setStatus(GameStatus.FINISHED);
        game.setEndedAt(Instant.now());

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
