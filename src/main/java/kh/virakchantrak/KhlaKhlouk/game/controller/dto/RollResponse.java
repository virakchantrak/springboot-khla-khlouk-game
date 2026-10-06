package kh.virakchantrak.KhlaKhlouk.game.controller.dto;

import kh.virakchantrak.KhlaKhlouk.game.domain.GameStatus;
import kh.virakchantrak.KhlaKhlouk.game.domain.Symbol;

import java.util.UUID;

public record RollResponse(
        UUID gameId,
        GameStatus status,
        Symbol dice1,
        Symbol dice2,
        Symbol dice3
) {
}
