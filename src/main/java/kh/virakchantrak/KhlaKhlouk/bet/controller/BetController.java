package kh.virakchantrak.KhlaKhlouk.bet.controller;

import jakarta.validation.Valid;
import kh.virakchantrak.KhlaKhlouk.bet.controller.dto.BetResponse;
import kh.virakchantrak.KhlaKhlouk.bet.controller.dto.PlaceBetRequest;
import kh.virakchantrak.KhlaKhlouk.bet.domain.Bet;
import kh.virakchantrak.KhlaKhlouk.bet.service.BetService;
import kh.virakchantrak.KhlaKhlouk.common.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class BetController {

    private final BetService betService;

    @PostMapping("/games/{gameId}/bets")
    public BetResponse placeBet(
            @PathVariable UUID gameId,
            @Valid @RequestBody PlaceBetRequest request
    ) {
        Bet bet = betService.placeBet(
                gameId,
                request.symbol(),
                request.amount()
        );

        return toResponse(bet);
    }

    @GetMapping("/players/{playerId}/bets")
    public PageResponse<BetResponse> getPlayerBets(
            @PathVariable UUID playerId,
            @PageableDefault(size = 10) Pageable pageable
    ) {
        Page<Bet> bets = betService.getPlayerBets(
                playerId,
                pageable
        );

        return new PageResponse<>(
                bets.getContent()
                        .stream()
                        .map(this::toResponse)
                        .toList(),
                bets.getNumber(),
                bets.getSize(),
                bets.getTotalElements(),
                bets.getTotalPages()
        );
    }

    private @NonNull BetResponse toResponse(Bet bet) {
        return new BetResponse(
                bet.getId(),
                bet.getGame().getId(),
                bet.getPlayer().getId(),
                bet.getSymbol(),
                bet.getAmount(),
                bet.getPayout()
        );
    }
}
