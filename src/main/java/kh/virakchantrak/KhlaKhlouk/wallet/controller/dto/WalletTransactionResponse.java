package kh.virakchantrak.KhlaKhlouk.wallet.controller.dto;

import kh.virakchantrak.KhlaKhlouk.wallet.domain.WalletTransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record WalletTransactionResponse(
        UUID id,
        WalletTransactionType type,
        BigDecimal amount,
        BigDecimal balanceAfter,
        UUID referenceId,
        Instant createdAt
) {
}