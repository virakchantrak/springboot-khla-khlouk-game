package kh.virakchantrak.KhlaKhlouk.player.repository;

import kh.virakchantrak.KhlaKhlouk.player.domain.Player;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface PlayerRepository extends JpaRepository<Player, UUID> {

    Optional<Player> findByUsername(String username);

    @Query("SELECT p FROM Player p WHERE lower(p.username) = lower(:username)")
    Optional<Player> findByUsernameIgnoreCase(String username);
}
