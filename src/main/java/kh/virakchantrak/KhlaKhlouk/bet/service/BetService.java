package kh.virakchantrak.KhlaKhlouk.bet.service;

import kh.virakchantrak.KhlaKhlouk.bet.domain.Bet;
import kh.virakchantrak.KhlaKhlouk.game.domain.Symbol;

import java.math.BigDecimal;
import java.util.UUID;

public interface BetService {

    Bet placeBet(
            UUID gameId,
            UUID playerId,
            Symbol symbol,
            BigDecimal amount
    );
}
