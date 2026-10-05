package kh.virakchantrak.KhlaKhlouk.game.repository;

import kh.virakchantrak.KhlaKhlouk.game.domain.GameResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface GameResultRepository extends JpaRepository<GameResult, UUID> {

    Optional<GameResult> findByGameId(UUID gameId);
}
