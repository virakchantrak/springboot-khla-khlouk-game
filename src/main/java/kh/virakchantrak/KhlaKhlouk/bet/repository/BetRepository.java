package kh.virakchantrak.KhlaKhlouk.bet.repository;

import kh.virakchantrak.KhlaKhlouk.bet.domain.Bet;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface BetRepository extends JpaRepository<Bet, UUID> {

    List<Bet> findByGameId(UUID gameId);

    List<Bet> findByPlayerId(UUID playerId);

    Page<Bet> findByPlayerIdOrderByCreatedAtDesc(
            UUID playerId,
            Pageable pageable
    );

    @Query("""
        SELECT COUNT(b)
        FROM Bet b
        WHERE b.game.id = :gameId
        """)
    long countByGameId(UUID gameId);

    @Query("""
        SELECT COALESCE(SUM(b.amount), 0)
        FROM Bet b
        WHERE b.game.id = :gameId
        """)
    BigDecimal sumAmountByGameId(UUID gameId);
}
