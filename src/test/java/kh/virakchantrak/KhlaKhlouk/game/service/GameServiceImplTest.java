package kh.virakchantrak.KhlaKhlouk.game.service;

import kh.virakchantrak.KhlaKhlouk.bet.domain.Bet;
import kh.virakchantrak.KhlaKhlouk.bet.repository.BetRepository;
import kh.virakchantrak.KhlaKhlouk.common.exception.BusinessException;
import kh.virakchantrak.KhlaKhlouk.game.domain.Dice;
import kh.virakchantrak.KhlaKhlouk.game.domain.Game;
import kh.virakchantrak.KhlaKhlouk.game.domain.GameResult;
import kh.virakchantrak.KhlaKhlouk.game.domain.GameStatus;
import kh.virakchantrak.KhlaKhlouk.game.domain.PayoutCalculator;
import kh.virakchantrak.KhlaKhlouk.game.domain.RollResult;
import kh.virakchantrak.KhlaKhlouk.game.domain.Symbol;
import kh.virakchantrak.KhlaKhlouk.game.repository.GameRepository;
import kh.virakchantrak.KhlaKhlouk.game.repository.GameResultRepository;
import kh.virakchantrak.KhlaKhlouk.player.domain.Player;
import kh.virakchantrak.KhlaKhlouk.wallet.domain.WalletTransaction;
import kh.virakchantrak.KhlaKhlouk.wallet.domain.WalletTransactionType;
import kh.virakchantrak.KhlaKhlouk.wallet.repository.WalletTransactionRepository;
import kh.virakchantrak.KhlaKhlouk.websocket.GameEvent;
import kh.virakchantrak.KhlaKhlouk.websocket.GameEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GameServiceImplTest {

    @Mock GameRepository gameRepository;
    @Mock BetRepository betRepository;
    @Mock GameResultRepository gameResultRepository;
    @Mock Dice dice;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock WalletTransactionRepository walletTransactionRepository;

    @Spy PayoutCalculator payoutCalculator = new PayoutCalculator();

    @InjectMocks GameServiceImpl gameService;

    private final UUID gameId = UUID.randomUUID();
    private Game game;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.setId(gameId);
        game.setStatus(GameStatus.BETTING);
    }

    @Test
    void shouldPayWinnersRecordPayoutAndFinishGame() {
        // each player already paid their 10 stake, so balances start at 90
        Player winner = playerWithBalance("90.00");
        Player loser = playerWithBalance("90.00");

        Bet winningBet = bet(winner, Symbol.TIGER, "10.00");   // TIGER appears twice
        Bet losingBet = bet(loser, Symbol.SHRIMP, "10.00");    // SHRIMP never appears

        when(gameRepository.findByIdForUpdate(gameId)).thenReturn(Optional.of(game));
        when(dice.rollThree()).thenReturn(
                new RollResult(Symbol.TIGER, Symbol.TIGER, Symbol.CRAB));
        when(betRepository.findByGameId(gameId))
                .thenReturn(List.of(winningBet, losingBet));

        GameResult result = gameService.rollGame(gameId);

        // dice stored in the result
        assertThat(result.getDice1()).isEqualTo(Symbol.TIGER);
        assertThat(result.getDice2()).isEqualTo(Symbol.TIGER);
        assertThat(result.getDice3()).isEqualTo(Symbol.CRAB);

        // 2 matches pay 3x the stake: 30
        assertThat(winningBet.getPayout()).isEqualByComparingTo("30.00");
        assertThat(winner.getBalance()).isEqualByComparingTo("120.00");

        // no match pays nothing
        assertThat(losingBet.getPayout()).isEqualByComparingTo("0");
        assertThat(loser.getBalance()).isEqualByComparingTo("90.00");

        // exactly one PAYOUT entry, only for the winner
        ArgumentCaptor<WalletTransaction> txCaptor =
                ArgumentCaptor.forClass(WalletTransaction.class);
        verify(walletTransactionRepository).save(txCaptor.capture());
        WalletTransaction tx = txCaptor.getValue();
        assertThat(tx.getType()).isEqualTo(WalletTransactionType.PAYOUT);
        assertThat(tx.getAmount()).isEqualByComparingTo("30.00");
        assertThat(tx.getBalanceAfter()).isEqualByComparingTo("120.00");
        assertThat(tx.getPlayer()).isSameAs(winner);

        // game is finished
        assertThat(game.getStatus()).isEqualTo(GameStatus.FINISHED);
        assertThat(game.getEndedAt()).isNotNull();

        // websocket event carries the dice
        ArgumentCaptor<GameEvent> eventCaptor = ArgumentCaptor.forClass(GameEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        GameEvent event = eventCaptor.getValue();
        assertThat(event.type()).isEqualTo(GameEventType.GAME_FINISHED);
        assertThat(event.dice1()).isEqualTo(Symbol.TIGER);
        assertThat(event.dice3()).isEqualTo(Symbol.CRAB);
    }

    @Test
    void shouldNotRollWhenGameIsNotInBettingStatus() {
        game.setStatus(GameStatus.WAITING);

        when(gameRepository.findByIdForUpdate(gameId)).thenReturn(Optional.of(game));

        assertThatThrownBy(() -> gameService.rollGame(gameId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "GAME_NOT_ACCEPTING_BETS");

        assertThat(game.getStatus()).isEqualTo(GameStatus.WAITING);
        verifyNoInteractions(dice, walletTransactionRepository, eventPublisher);
    }

    private Player playerWithBalance(String balance) {
        Player player = new Player();
        player.setUsername("player-" + UUID.randomUUID());
        player.setBalance(new BigDecimal(balance));
        return player;
    }

    private Bet bet(Player player, Symbol symbol, String amount) {
        Bet bet = new Bet();
        bet.setGame(game);
        bet.setPlayer(player);
        bet.setSymbol(symbol);
        bet.setAmount(new BigDecimal(amount));
        bet.setPayout(BigDecimal.ZERO);
        return bet;
    }

    @Test
    void shouldStartBettingAndPublishEvent() {
        game.setStatus(GameStatus.WAITING);

        when(gameRepository.findById(gameId)).thenReturn(Optional.of(game));

        Game result = gameService.startBetting(gameId);

        assertThat(result.getStatus()).isEqualTo(GameStatus.BETTING);
        assertThat(result.getStartedAt()).isNotNull();

        ArgumentCaptor<GameEvent> captor = ArgumentCaptor.forClass(GameEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().type()).isEqualTo(GameEventType.BETTING_OPENED);
        assertThat(captor.getValue().status()).isEqualTo(GameStatus.BETTING);
    }

    @Test
    void shouldNotStartBettingWhenGameIsNotWaiting() {
        // the game is already in BETTING from setUp()
        when(gameRepository.findById(gameId)).thenReturn(Optional.of(game));

        assertThatThrownBy(() -> gameService.startBetting(gameId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "GAME_NOT_WAITING");

        verifyNoInteractions(eventPublisher);
    }
}