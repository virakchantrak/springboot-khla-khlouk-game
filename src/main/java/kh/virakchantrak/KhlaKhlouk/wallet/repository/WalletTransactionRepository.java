package kh.virakchantrak.KhlaKhlouk.wallet.repository;

import kh.virakchantrak.KhlaKhlouk.wallet.domain.WalletTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface WalletTransactionRepository
        extends JpaRepository<WalletTransaction, UUID> {

    Page<WalletTransaction> findByPlayerIdOrderByCreatedAtDesc(
            UUID playerId,
            Pageable pageable
    );
}