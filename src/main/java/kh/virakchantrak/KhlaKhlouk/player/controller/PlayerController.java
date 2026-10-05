package kh.virakchantrak.KhlaKhlouk.player.controller;

import kh.virakchantrak.KhlaKhlouk.player.controller.dto.CreatePlayerRequest;
import kh.virakchantrak.KhlaKhlouk.player.controller.dto.PlayerResponse;
import kh.virakchantrak.KhlaKhlouk.player.domain.Player;
import kh.virakchantrak.KhlaKhlouk.player.service.PlayerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/players")
@RequiredArgsConstructor
public class PlayerController {

    private final PlayerService playerService;

    @PostMapping
    public PlayerResponse createPlayer(
            @RequestBody CreatePlayerRequest request
    ) {
        Player player = playerService.createPlayer(
                request.username(),
                request.initialBalance()
        );

        return toResponse(player);
    }

    @GetMapping("/{playerId}")
    public PlayerResponse getPlayer(
            @PathVariable UUID playerId
    ) {
        return toResponse(
                playerService.getPlayer(playerId)
        );
    }

    private PlayerResponse toResponse(Player player) {
        return new PlayerResponse(
                player.getId(),
                player.getUsername(),
                player.getBalance()
        );
    }
}
