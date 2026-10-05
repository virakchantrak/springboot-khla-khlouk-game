package kh.virakchantrak.KhlaKhlouk.player.repository;

import kh.virakchantrak.KhlaKhlouk.player.domain.Player;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PlayerRepository extends JpaRepository<Player, UUID> {

    Optional<Player> findByUsername(String username);
}
