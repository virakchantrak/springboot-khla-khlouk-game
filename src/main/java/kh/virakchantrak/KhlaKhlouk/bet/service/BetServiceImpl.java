package kh.virakchantrak.KhlaKhlouk.bet.service;

import kh.virakchantrak.KhlaKhlouk.auth.service.CurrentPlayerService;
import kh.virakchantrak.KhlaKhlouk.bet.domain.Bet;
import kh.virakchantrak.KhlaKhlouk.bet.repository.BetRepository;
import kh.virakchantrak.KhlaKhlouk.common.exception.BusinessException;
import kh.virakchantrak.KhlaKhlouk.game.domain.Game;
import kh.virakchantrak.KhlaKhlouk.game.domain.GameStatus;
import kh.virakchantrak.KhlaKhlouk.game.domain.Symbol;
import kh.virakchantrak.KhlaKhlouk.game.repository.GameRepository;
import kh.virakchantrak.KhlaKhlouk.player.domain.Player;
import kh.virakchantrak.KhlaKhlouk.player.repository.PlayerRepository;
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
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class BetServiceImpl implements BetService {

    private final GameRepository gameRepository;
    private final PlayerRepository playerRepository;
    private final BetRepository betRepository;
    private final CurrentPlayerService currentPlayerService;
    private final ApplicationEventPublisher eventPublisher;
    private final WalletTransactionRepository walletTransactionRepository;

    @Override
    public Bet placeBet(
            UUID gameId,
            Symbol symbol,
            BigDecimal amount
    ) {
        UUID playerId = currentPlayerService.getPlayerId();

        Game game = gameRepository.findById(gameId)
                .orElseThrow(() ->
                        new BusinessException("GAME_NOT_FOUND", "Game not found", HttpStatus.NOT_FOUND));

        if (game.getStatus() != GameStatus.BETTING) {
            throw new BusinessException(
                    "GAME_NOT_ACCEPTING_BETS",
                    "Game is not accepting bets",
                    HttpStatus.BAD_REQUEST
            );
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(
                    "BET_AMOUNT_INVALID",
                    "Bet amount must be greater than zero",
                    HttpStatus.BAD_REQUEST
            );
        }

        Player player = playerRepository.findById(playerId)
                .orElseThrow(() ->
                        new BusinessException("PLAYER_NOT_FOUND", "Player not found", HttpStatus.NOT_FOUND));

        if (player.getBalance().compareTo(amount) < 0) {
            throw new BusinessException(
                    "INSUFFICIENT_BALANCE",
                    "Insufficient balance",
                    HttpStatus.BAD_REQUEST
            );
        }

        player.setBalance(
                player.getBalance().subtract(amount)
        );

        Bet bet = new Bet();
        bet.setGame(game);
        bet.setPlayer(player);
        bet.setSymbol(symbol);
        bet.setAmount(amount);
        bet.setPayout(BigDecimal.ZERO);

        Bet saved = betRepository.save(bet);

        WalletTransaction transaction = new WalletTransaction();
        transaction.setPlayer(player);
        transaction.setType(WalletTransactionType.BET);
        transaction.setAmount(amount);
        transaction.setBalanceAfter(player.getBalance());
        transaction.setReferenceId(saved.getId());
        walletTransactionRepository.save(transaction);

        eventPublisher.publishEvent(
                new GameEvent(
                        GameEventType.BET_PLACED,
                        game.getId(),
                        game.getStatus(),
                        null,
                        null,
                        null,
                        (int) betRepository.countByGameId(gameId),
                        betRepository.sumAmountByGameId(gameId)
                )
        );

        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Bet> getPlayerBets(
            UUID playerId,
            Pageable pageable
    ) {
        playerRepository.findById(playerId)
                .orElseThrow(() ->
                        new BusinessException(
                                "PLAYER_NOT_FOUND",
                                "Player not found",
                                HttpStatus.NOT_FOUND
                        )
                );

        return betRepository.findByPlayerIdOrderByCreatedAtDesc(
                playerId,
                pageable
        );
    }
}
