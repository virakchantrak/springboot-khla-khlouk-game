package kh.virakchantrak.KhlaKhlouk.bet.service.impl;

import kh.virakchantrak.KhlaKhlouk.bet.domain.Bet;
import kh.virakchantrak.KhlaKhlouk.bet.repository.BetRepository;
import kh.virakchantrak.KhlaKhlouk.bet.service.BetService;
import kh.virakchantrak.KhlaKhlouk.game.domain.GameStatus;
import kh.virakchantrak.KhlaKhlouk.game.domain.Symbol;
import kh.virakchantrak.KhlaKhlouk.game.domain.Game;
import kh.virakchantrak.KhlaKhlouk.game.repository.GameRepository;
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
public class BetServiceImpl implements BetService {

    private final GameRepository gameRepository;
    private final PlayerRepository playerRepository;
    private final BetRepository betRepository;

    @Override
    public Bet placeBet(
            UUID gameId,
            UUID playerId,
            Symbol symbol,
            BigDecimal amount
    ) {
        Game game = gameRepository.findById(gameId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Game not found"));

        if (game.getStatus() != GameStatus.BETTING) {
            throw new IllegalStateException(
                    "Game is not accepting bets"
            );
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Bet amount must be greater than zero"
            );
        }

        Player player = playerRepository.findById(playerId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Player not found"));

        if (player.getBalance().compareTo(amount) < 0) {
            throw new IllegalStateException(
                    "Insufficient balance"
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

        return betRepository.save(bet);
    }
}
