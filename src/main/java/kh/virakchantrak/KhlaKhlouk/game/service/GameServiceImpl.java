package kh.virakchantrak.KhlaKhlouk.game.service;

import kh.virakchantrak.KhlaKhlouk.bet.domain.Bet;
import kh.virakchantrak.KhlaKhlouk.bet.repository.BetRepository;
import kh.virakchantrak.KhlaKhlouk.common.exception.BusinessException;
import kh.virakchantrak.KhlaKhlouk.game.controller.dto.GameDetailsResponse;
import kh.virakchantrak.KhlaKhlouk.game.domain.Dice;
import kh.virakchantrak.KhlaKhlouk.game.domain.Game;
import kh.virakchantrak.KhlaKhlouk.game.domain.GameResult;
import kh.virakchantrak.KhlaKhlouk.game.domain.GameStatus;
import kh.virakchantrak.KhlaKhlouk.game.domain.PayoutCalculator;
import kh.virakchantrak.KhlaKhlouk.game.domain.RollResult;
import kh.virakchantrak.KhlaKhlouk.game.repository.GameRepository;
import kh.virakchantrak.KhlaKhlouk.game.repository.GameResultRepository;
import kh.virakchantrak.KhlaKhlouk.player.domain.Player;
import kh.virakchantrak.KhlaKhlouk.wallet.domain.WalletTransaction;
import kh.virakchantrak.KhlaKhlouk.wallet.domain.WalletTransactionType;
import kh.virakchantrak.KhlaKhlouk.wallet.repository.WalletTransactionRepository;
import kh.virakchantrak.KhlaKhlouk.websocket.GameEvent;
import kh.virakchantrak.KhlaKhlouk.websocket.GameEventType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
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
    private final ApplicationEventPublisher eventPublisher;
    private final WalletTransactionRepository walletTransactionRepository;

    @Override
    public Game createGame() {
        Game game = new Game();
        game.setStatus(GameStatus.WAITING);

        return gameRepository.save(game);
    }

    @Override
    public Game startBetting(UUID gameId) {
        Game game = getGameById(gameId);

        if (game.getStatus() != GameStatus.WAITING) {
            throw new BusinessException(
                    "GAME_NOT_WAITING",
                    "Game is not waiting",
                    HttpStatus.BAD_REQUEST
            );
        }

        game.setStatus(GameStatus.BETTING);
        game.setStartedAt(Instant.now());

        eventPublisher.publishEvent(
                new GameEvent(
                        GameEventType.BETTING_OPENED,
                        game.getId(),
                        game.getStatus(),
                        null,
                        null,
                        null,
                        null,
                        null
                )
        );

        return game;
    }

    @Override
    public GameResult rollGame(UUID gameId) {

        Game game = getGameById(gameId);

        if (game.getStatus() != GameStatus.BETTING) {
            throw new BusinessException(
                    "GAME_NOT_ACCEPTING_BETS",
                    "Game is not accepting bets",
                    HttpStatus.BAD_REQUEST
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

                WalletTransaction transaction = new WalletTransaction();
                transaction.setPlayer(player);
                transaction.setType(WalletTransactionType.PAYOUT);
                transaction.setAmount(payout);
                transaction.setBalanceAfter(player.getBalance());
                transaction.setReferenceId(bet.getId());
                walletTransactionRepository.save(transaction);
            }
        }

        // 5. Finish the game
        game.setStatus(GameStatus.FINISHED);
        game.setEndedAt(Instant.now());

        eventPublisher.publishEvent(
                new GameEvent(
                        GameEventType.GAME_FINISHED,
                        game.getId(),
                        game.getStatus(),
                        gameResult.getDice1(),
                        gameResult.getDice2(),
                        gameResult.getDice3(),
                        null,
                        null
                )
        );

        return gameResult;
    }

    @Override
    @Transactional(readOnly = true)
    public GameResult getGameResult(UUID gameId) {

        Game game = getGameById(gameId);

        if (game.getStatus() != GameStatus.FINISHED) {
            throw new BusinessException(
                    "GAME_NOT_FINISHED",
                    "Game has not finished yet",
                    HttpStatus.BAD_REQUEST
            );
        }

        return gameResultRepository.findByGameId(gameId)
                .orElseThrow(() ->
                        new BusinessException(
                                "GAME_RESULT_NOT_FOUND",
                                "Game result not found",
                                HttpStatus.NOT_FOUND
                        )
                );
    }

    private Game getGameById(UUID gameId) {
        return gameRepository.findById(gameId)
                .orElseThrow(() ->
                        new BusinessException(
                                "GAME_NOT_FOUND",
                                "Game not found",
                                HttpStatus.NOT_FOUND
                        ));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Game> getGames(Pageable pageable) {
        return gameRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public GameDetailsResponse getGameDetails(UUID gameId) {

        Game game = getGameById(gameId);

        GameResult result = gameResultRepository
                .findByGameId(gameId)
                .orElse(null);

        long totalBets = betRepository.countByGameId(gameId);

        BigDecimal totalBetAmount =
                betRepository.sumAmountByGameId(gameId);

        return new GameDetailsResponse(
                game.getId(),
                game.getStatus(),
                game.getStartedAt(),
                game.getEndedAt(),
                result != null ? result.getDice1() : null,
                result != null ? result.getDice2() : null,
                result != null ? result.getDice3() : null,
                (int) totalBets,
                totalBetAmount
        );
    }
}
