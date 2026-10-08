package kh.virakchantrak.KhlaKhlouk.wallet.service;

import kh.virakchantrak.KhlaKhlouk.wallet.domain.WalletTransaction;
import kh.virakchantrak.KhlaKhlouk.wallet.repository.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WalletServiceImpl implements WalletService {

    private final WalletTransactionRepository walletTransactionRepository;

    @Override
    public Page<WalletTransaction> getTransactions(
            UUID playerId,
            Pageable pageable
    ) {
        return walletTransactionRepository
                .findByPlayerIdOrderByCreatedAtDesc(playerId, pageable);
    }
}