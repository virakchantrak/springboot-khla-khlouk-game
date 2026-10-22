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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
                .hasFieldOrPropertyWithValue("code", "GAME_NOT_READY_TO_ROLL");

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

    @Test
    void cancellingABettingGameRefundsEveryBetAndRecordsTheLedger() {
        // stakes were already paid: A bet 2 x 10 (100 -> 80), B bet 10 (100 -> 90)
        Player playerA = playerWithBalance("80.00");
        Player playerB = playerWithBalance("90.00");

        Bet a1 = bet(playerA, Symbol.TIGER, "10.00");
        Bet a2 = bet(playerA, Symbol.CRAB, "10.00");
        Bet b1 = bet(playerB, Symbol.FISH, "10.00");

        UUID a1Id = UUID.randomUUID();
        UUID a2Id = UUID.randomUUID();
        UUID b1Id = UUID.randomUUID();
        ReflectionTestUtils.setField(a1, "id", a1Id);
        ReflectionTestUtils.setField(a2, "id", a2Id);
        ReflectionTestUtils.setField(b1, "id", b1Id);

        when(gameRepository.findByIdForUpdate(gameId)).thenReturn(Optional.of(game));
        when(betRepository.findByGameId(gameId)).thenReturn(List.of(a1, a2, b1));

        Game cancelled = gameService.cancelGame(gameId);

        assertThat(cancelled.getStatus()).isEqualTo(GameStatus.CANCELLED);
        assertThat(cancelled.getEndedAt()).isNotNull();

        // everybody has their stakes back
        assertThat(playerA.getBalance()).isEqualByComparingTo("100.00");
        assertThat(playerB.getBalance()).isEqualByComparingTo("100.00");

        // one REFUND entry per bet, linked to that bet, with the balance after each refund
        ArgumentCaptor<WalletTransaction> captor =
                ArgumentCaptor.forClass(WalletTransaction.class);
        verify(walletTransactionRepository, times(3)).save(captor.capture());
        List<WalletTransaction> entries = captor.getAllValues();

        assertThat(entries).extracting(WalletTransaction::getType)
                .containsOnly(WalletTransactionType.REFUND);
        assertThat(entries).extracting(WalletTransaction::getReferenceId)
                .containsExactly(a1Id, a2Id, b1Id);
        assertThat(entries)
                .extracting(entry -> entry.getBalanceAfter().toPlainString())
                .containsExactly("90.00", "100.00", "100.00");

        // the event says what happened
        ArgumentCaptor<GameEvent> eventCaptor = ArgumentCaptor.forClass(GameEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        GameEvent event = eventCaptor.getValue();
        assertThat(event.type()).isEqualTo(GameEventType.GAME_CANCELLED);
        assertThat(event.status()).isEqualTo(GameStatus.CANCELLED);
        assertThat(event.totalBets()).isEqualTo(3);
        assertThat(event.totalBetAmount()).isEqualByComparingTo("30.00");
        assertThat(event.dice1()).isNull();

        // the exclusive lock is what keeps a late bet or a roll from sneaking in
        verify(gameRepository).findByIdForUpdate(gameId);
        verify(gameRepository, never()).findById(any());
    }

    @Test
    void cancellingAWaitingGameWithNoBetsJustCancelsIt() {
        game.setStatus(GameStatus.WAITING);

        when(gameRepository.findByIdForUpdate(gameId)).thenReturn(Optional.of(game));
        when(betRepository.findByGameId(gameId)).thenReturn(List.of());

        Game cancelled = gameService.cancelGame(gameId);

        assertThat(cancelled.getStatus()).isEqualTo(GameStatus.CANCELLED);
        verifyNoInteractions(walletTransactionRepository);

        ArgumentCaptor<GameEvent> eventCaptor = ArgumentCaptor.forClass(GameEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().totalBets()).isZero();
        assertThat(eventCaptor.getValue().totalBetAmount()).isEqualByComparingTo("0");
    }

    @ParameterizedTest
    @EnumSource(value = GameStatus.class, names = {"FINISHED", "CANCELLED"})
    void gamesThatAreAlreadyOverCannotBeCancelled(GameStatus status) {
        game.setStatus(status);

        when(gameRepository.findByIdForUpdate(gameId)).thenReturn(Optional.of(game));

        assertThatThrownBy(() -> gameService.cancelGame(gameId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "GAME_NOT_CANCELLABLE");

        assertThat(game.getStatus()).isEqualTo(status);
        verifyNoInteractions(betRepository, walletTransactionRepository, eventPublisher);
    }

    @Test
    void cancellingAnUnknownGameReturnsGameNotFound() {
        when(gameRepository.findByIdForUpdate(gameId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> gameService.cancelGame(gameId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "GAME_NOT_FOUND");

        verifyNoInteractions(betRepository, walletTransactionRepository, eventPublisher);
    }
}