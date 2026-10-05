package kh.virakchantrak.KhlaKhlouk.game.controller;

import kh.virakchantrak.KhlaKhlouk.game.controller.dto.GameResponse;
import kh.virakchantrak.KhlaKhlouk.game.domain.Game;
import kh.virakchantrak.KhlaKhlouk.game.service.GameService;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/games")
@RequiredArgsConstructor
public class GameController {

    private final GameService gameService;

    @PostMapping
    public GameResponse createGame() {

        Game game = gameService.createGame();

        return toResponse(game);
    }

    @PostMapping("/{gameId}/betting")
    public GameResponse bettingGame(@PathVariable UUID gameId) {

        Game game = gameService.startBetting(gameId);

        return toResponse(game);
    }

    @PostMapping("/{gameId}/roll")
    public GameResponse rollGame(@PathVariable UUID gameId) {

        Game game = gameService.rollGame(gameId);

        return toResponse(game);
    }

    private static @NonNull GameResponse toResponse(Game game) {
        return new GameResponse(
                game.getId(),
                game.getStatus(),
                game.getCreatedAt()
        );
    }
}
