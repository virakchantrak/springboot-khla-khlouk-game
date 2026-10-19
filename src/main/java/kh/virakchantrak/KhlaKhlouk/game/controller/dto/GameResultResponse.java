package kh.virakchantrak.KhlaKhlouk.game.controller.dto;

import kh.virakchantrak.KhlaKhlouk.game.domain.Symbol;

import java.util.UUID;

public record GameResultResponse(
        UUID gameId,
        Symbol dice1,
        Symbol dice2,
        Symbol dice3
) {
}
