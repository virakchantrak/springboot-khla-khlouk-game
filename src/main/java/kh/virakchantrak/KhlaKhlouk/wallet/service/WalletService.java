package kh.virakchantrak.KhlaKhlouk.wallet.service;

import kh.virakchantrak.KhlaKhlouk.wallet.domain.WalletTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface WalletService {

    Page<WalletTransaction> getTransactions(UUID playerId, Pageable pageable);
}