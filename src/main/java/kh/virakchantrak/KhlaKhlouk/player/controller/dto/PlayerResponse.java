package kh.virakchantrak.KhlaKhlouk.player.controller.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PlayerResponse(
        UUID id,
        String username,
        BigDecimal balance
) {
}
