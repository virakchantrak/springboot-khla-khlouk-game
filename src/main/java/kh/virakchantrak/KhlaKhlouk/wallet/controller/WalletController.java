package kh.virakchantrak.KhlaKhlouk.wallet.controller;

import kh.virakchantrak.KhlaKhlouk.auth.service.CurrentPlayerService;
import kh.virakchantrak.KhlaKhlouk.common.response.PageResponse;
import kh.virakchantrak.KhlaKhlouk.wallet.controller.dto.WalletTransactionResponse;
import kh.virakchantrak.KhlaKhlouk.wallet.domain.WalletTransaction;
import kh.virakchantrak.KhlaKhlouk.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;
    private final CurrentPlayerService currentPlayerService;

    @PreAuthorize("hasRole('PLAYER')")
    @GetMapping("/transactions")
    public PageResponse<WalletTransactionResponse> getTransactions(
            @PageableDefault(size = 10) Pageable pageable
    ) {
        Page<WalletTransaction> transactions = walletService.getTransactions(
                currentPlayerService.getPlayerId(),
                pageable
        );

        return new PageResponse<>(
                transactions.getContent()
                        .stream()
                        .map(this::toResponse)
                        .toList(),
                transactions.getNumber(),
                transactions.getSize(),
                transactions.getTotalElements(),
                transactions.getTotalPages()
        );
    }

    private WalletTransactionResponse toResponse(WalletTransaction transaction) {
        return new WalletTransactionResponse(
                transaction.getId(),
                transaction.getType(),
                transaction.getAmount(),
                transaction.getBalanceAfter(),
                transaction.getReferenceId(),
                transaction.getCreatedAt()
        );
    }
}