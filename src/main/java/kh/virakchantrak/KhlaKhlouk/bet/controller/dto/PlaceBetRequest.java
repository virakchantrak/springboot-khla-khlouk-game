package kh.virakchantrak.KhlaKhlouk.bet.controller.dto;

import kh.virakchantrak.KhlaKhlouk.game.domain.Symbol;

import java.math.BigDecimal;
import java.util.UUID;

public record PlaceBetRequest(
        UUID playerId,
        Symbol symbol,
        BigDecimal amount
) {
}
