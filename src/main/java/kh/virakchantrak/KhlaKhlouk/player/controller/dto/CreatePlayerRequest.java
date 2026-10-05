package kh.virakchantrak.KhlaKhlouk.player.controller.dto;

import java.math.BigDecimal;

public record CreatePlayerRequest(
        String username,
        BigDecimal initialBalance
) {
}
