package kh.virakchantrak.KhlaKhlouk.bet.controller.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import kh.virakchantrak.KhlaKhlouk.game.domain.Symbol;

import java.math.BigDecimal;

public record PlaceBetRequest(

        @NotNull(message = "Symbol is required")
        Symbol symbol,

        @NotNull(message = "Bet amount is required")
        @DecimalMin(
                value = "0.01",
                message = "Bet amount must be greater than zero"
        )
        BigDecimal amount
) {
}
