package kh.virakchantrak.KhlaKhlouk.bet.repository;

import kh.virakchantrak.KhlaKhlouk.bet.domain.Bet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BetRepository extends JpaRepository<Bet, UUID> {

    List<Bet> findByGameId(UUID gameId);

    List<Bet> findByPlayerId(UUID playerId);
}
