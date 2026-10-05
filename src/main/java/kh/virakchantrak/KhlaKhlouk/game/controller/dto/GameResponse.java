package kh.virakchantrak.KhlaKhlouk.game.controller.dto;

import kh.virakchantrak.KhlaKhlouk.game.domain.GameStatus;

import java.time.Instant;
import java.util.UUID;

public record GameResponse(
        UUID id,
        GameStatus status,
        Instant createdAt
) {
}
