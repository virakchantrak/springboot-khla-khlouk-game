package kh.virakchantrak.KhlaKhlouk.bet.service;

import kh.virakchantrak.KhlaKhlouk.bet.domain.Bet;
import kh.virakchantrak.KhlaKhlouk.game.domain.Symbol;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.UUID;

public interface BetService {

    Bet placeBet(
            UUID gameId,
            Symbol symbol,
            BigDecimal amount
    );

    Page<Bet> getPlayerBets(
            UUID playerId,
            Pageable pageable
    );
}
