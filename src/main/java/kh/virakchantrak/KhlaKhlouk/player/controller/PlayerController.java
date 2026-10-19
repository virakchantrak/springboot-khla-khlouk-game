package kh.virakchantrak.KhlaKhlouk.player.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import kh.virakchantrak.KhlaKhlouk.auth.audit.SecurityAudit;
import kh.virakchantrak.KhlaKhlouk.auth.service.CurrentPlayerService;
import kh.virakchantrak.KhlaKhlouk.auth.service.LoginAttemptService;
import kh.virakchantrak.KhlaKhlouk.common.exception.BusinessException;
import kh.virakchantrak.KhlaKhlouk.player.controller.dto.CreatePlayerRequest;
import kh.virakchantrak.KhlaKhlouk.player.controller.dto.PlayerResponse;
import kh.virakchantrak.KhlaKhlouk.player.domain.Player;
import kh.virakchantrak.KhlaKhlouk.player.service.PlayerService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
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
    private final CurrentPlayerService currentPlayerService;
    private final LoginAttemptService loginAttemptService;

    @PostMapping
    public PlayerResponse createPlayer(
            @Valid @RequestBody CreatePlayerRequest request,
            HttpServletRequest httpRequest
    ) {
        String ip = httpRequest.getRemoteAddr();

        // counted before the expensive password hashing happens
        try {
            loginAttemptService.registerRegistrationAttempt(ip);
        } catch (BusinessException e) {
            SecurityAudit.registrationBlocked(ip);
            throw e;
        }

        Player player = playerService.createPlayer(
                request.username(),
                request.password()
        );

        SecurityAudit.playerRegistered(ip, player.getUsername());

        return toResponse(player);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{playerId}")
    public PlayerResponse getPlayer(
            @PathVariable UUID playerId
    ) {
        return toResponse(
                playerService.getPlayer(playerId)
        );
    }

    @GetMapping("/me")
    public PlayerResponse getCurrentPlayer() {
        Player player = playerService.getPlayer(
                currentPlayerService.getPlayerId()
        );

        return toResponse(player);
    }

    private PlayerResponse toResponse(Player player) {
        return new PlayerResponse(
                player.getId(),
                player.getUsername(),
                player.getBalance()
        );
    }
}
