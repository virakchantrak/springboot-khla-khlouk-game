package kh.virakchantrak.KhlaKhlouk.game.controller.dto;

import kh.virakchantrak.KhlaKhlouk.game.domain.GameStatus;
import kh.virakchantrak.KhlaKhlouk.game.domain.Symbol;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record GameDetailsResponse(
        UUID id,
        GameStatus status,
        Instant startedAt,
        Instant endedAt,
        Symbol dice1,
        Symbol dice2,
        Symbol dice3,
        int totalBets,
        BigDecimal totalBetAmount
) {
}
