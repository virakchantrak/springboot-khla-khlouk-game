package kh.virakchantrak.KhlaKhlouk.bet.controller;

import kh.virakchantrak.KhlaKhlouk.bet.controller.dto.BetResponse;
import kh.virakchantrak.KhlaKhlouk.bet.controller.dto.PlaceBetRequest;
import kh.virakchantrak.KhlaKhlouk.bet.domain.Bet;
import kh.virakchantrak.KhlaKhlouk.bet.service.BetService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/games/{gameId}/bets")
@RequiredArgsConstructor
public class BetController {

    private final BetService betService;

    @PostMapping
    public BetResponse placeBet(
            @PathVariable UUID gameId,
            @RequestBody PlaceBetRequest request
    ) {
        Bet bet = betService.placeBet(
                gameId,
                request.playerId(),
                request.symbol(),
                request.amount()
        );

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
