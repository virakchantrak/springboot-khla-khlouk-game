package kh.virakchantrak.KhlaKhlouk.game.controller;

import kh.virakchantrak.KhlaKhlouk.common.response.PageResponse;
import kh.virakchantrak.KhlaKhlouk.game.controller.dto.GameDetailsResponse;
import kh.virakchantrak.KhlaKhlouk.game.controller.dto.GameResponse;
import kh.virakchantrak.KhlaKhlouk.game.controller.dto.GameResultResponse;
import kh.virakchantrak.KhlaKhlouk.game.controller.dto.RollResponse;
import kh.virakchantrak.KhlaKhlouk.game.domain.Game;
import kh.virakchantrak.KhlaKhlouk.game.domain.GameResult;
import kh.virakchantrak.KhlaKhlouk.game.service.GameService;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
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
    public RollResponse rollGame(
            @PathVariable UUID gameId
    ) {
        GameResult result = gameService.rollGame(gameId);

        return new RollResponse(
                result.getGame().getId(),
                result.getGame().getStatus(),
                result.getDice1(),
                result.getDice2(),
                result.getDice3()
        );
    }

    @GetMapping("/{gameId}/result")
    public GameResultResponse getGameResult(
            @PathVariable UUID gameId
    ) {
        GameResult result = gameService.getGameResult(gameId);

        return new GameResultResponse(
                result.getGame().getId(),
                result.getDice1(),
                result.getDice2(),
                result.getDice3()
        );
    }

    @GetMapping
    public PageResponse<GameResponse> getGames(
            @PageableDefault() Pageable pageable
    ) {
        Page<Game> games = gameService.getGames(pageable);

        return new PageResponse<>(
                games.getContent()
                        .stream()
                        .map(this::toResponse)
                        .toList(),
                games.getNumber(),
                games.getSize(),
                games.getTotalElements(),
                games.getTotalPages()
        );
    }

    @GetMapping("/{gameId}")
    public GameDetailsResponse getGameDetails(
            @PathVariable UUID gameId
    ) {
        return gameService.getGameDetails(gameId);
    }

    private @NonNull GameResponse toResponse(Game game) {
        return new GameResponse(
                game.getId(),
                game.getStatus(),
                game.getCreatedAt()
        );
    }
}
