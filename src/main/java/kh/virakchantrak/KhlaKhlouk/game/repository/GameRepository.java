package kh.virakchantrak.KhlaKhlouk.game.repository;

import kh.virakchantrak.KhlaKhlouk.common.constant.GameStatus;
import kh.virakchantrak.KhlaKhlouk.game.domain.Game;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface GameRepository extends JpaRepository<Game, UUID> {

    List<Game> findByStatus(GameStatus status);
}
