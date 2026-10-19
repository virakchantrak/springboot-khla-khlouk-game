package kh.virakchantrak.KhlaKhlouk.bet.service;

import kh.virakchantrak.KhlaKhlouk.auth.service.CurrentPlayerService;
import kh.virakchantrak.KhlaKhlouk.bet.domain.Bet;
import kh.virakchantrak.KhlaKhlouk.bet.repository.BetRepository;
import kh.virakchantrak.KhlaKhlouk.common.exception.BusinessException;
import kh.virakchantrak.KhlaKhlouk.game.domain.Game;
import kh.virakchantrak.KhlaKhlouk.game.domain.GameStatus;
import kh.virakchantrak.KhlaKhlouk.game.domain.Symbol;
import kh.virakchantrak.KhlaKhlouk.game.repository.GameRepository;
import kh.virakchantrak.KhlaKhlouk.player.domain.Player;
import kh.virakchantrak.KhlaKhlouk.player.repository.PlayerRepository;
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
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BetServiceImplTest {

    @Mock GameRepository gameRepository;
    @Mock PlayerRepository playerRepository;
    @Mock BetRepository betRepository;
    @Mock CurrentPlayerService currentPlayerService;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock WalletTransactionRepository walletTransactionRepository;

    @InjectMocks BetServiceImpl betService;

    private final UUID gameId = UUID.randomUUID();
    private final UUID playerId = UUID.randomUUID();

    private Game game;
    private Player player;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.setId(gameId);
        game.setStatus(GameStatus.BETTING);

        player = new Player();
        player.setUsername("tester");
    }

    @Test
    void shouldRejectBetWhenBalanceIsTooLow() {
        player.setBalance(new BigDecimal("5.00"));

        when(currentPlayerService.getPlayerId()).thenReturn(playerId);
        when(gameRepository.findByIdForShare(gameId)).thenReturn(Optional.of(game));
        when(playerRepository.findById(playerId)).thenReturn(Optional.of(player));

        assertThatThrownBy(() ->
                betService.placeBet(gameId, Symbol.TIGER, new BigDecimal("10.00")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "INSUFFICIENT_BALANCE");

        assertThat(player.getBalance()).isEqualByComparingTo("5.00");
        verify(betRepository, never()).save(any());
        verifyNoInteractions(walletTransactionRepository, eventPublisher);
    }

    @Test
    void shouldPlaceBetAndRecordWalletEntryAndEvent() {
        player.setBalance(new BigDecimal("100.00"));

        when(currentPlayerService.getPlayerId()).thenReturn(playerId);
        when(gameRepository.findByIdForShare(gameId)).thenReturn(Optional.of(game));
        when(playerRepository.findById(playerId)).thenReturn(Optional.of(player));
        when(betRepository.save(any(Bet.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(betRepository.countByGameId(gameId)).thenReturn(1L);
        when(betRepository.sumAmountByGameId(gameId)).thenReturn(new BigDecimal("10.00"));

        Bet bet = betService.placeBet(gameId, Symbol.TIGER, new BigDecimal("10.00"));

        // the bet itself
        assertThat(bet.getSymbol()).isEqualTo(Symbol.TIGER);
        assertThat(bet.getAmount()).isEqualByComparingTo("10.00");
        assertThat(bet.getPayout()).isEqualByComparingTo("0");

        // the money moved
        assertThat(player.getBalance()).isEqualByComparingTo("90.00");

        // the wallet history
        ArgumentCaptor<WalletTransaction> txCaptor =
                ArgumentCaptor.forClass(WalletTransaction.class);
        verify(walletTransactionRepository).save(txCaptor.capture());
        WalletTransaction tx = txCaptor.getValue();
        assertThat(tx.getType()).isEqualTo(WalletTransactionType.BET);
        assertThat(tx.getAmount()).isEqualByComparingTo("10.00");
        assertThat(tx.getBalanceAfter()).isEqualByComparingTo("90.00");

        // the websocket event
        ArgumentCaptor<GameEvent> eventCaptor = ArgumentCaptor.forClass(GameEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        GameEvent event = eventCaptor.getValue();
        assertThat(event.type()).isEqualTo(GameEventType.BET_PLACED);
        assertThat(event.totalBets()).isEqualTo(1);
        assertThat(event.totalBetAmount()).isEqualByComparingTo("10.00");
    }

    @Test
    void shouldRejectBetWhenGameIsNotInBettingStatus() {
        game.setStatus(GameStatus.WAITING);

        when(gameRepository.findByIdForShare(gameId)).thenReturn(Optional.of(game));

        assertThatThrownBy(() ->
                betService.placeBet(gameId, Symbol.TIGER, new BigDecimal("10.00")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "GAME_NOT_ACCEPTING_BETS");

        verify(betRepository, never()).save(any());
        verifyNoInteractions(walletTransactionRepository, eventPublisher);
    }

    @Test
    void shouldRejectBetWhenGameDoesNotExist() {
        when(gameRepository.findByIdForShare(gameId)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                betService.placeBet(gameId, Symbol.TIGER, new BigDecimal("10.00")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "GAME_NOT_FOUND");

        verifyNoInteractions(
                playerRepository, betRepository,
                walletTransactionRepository, eventPublisher);
    }

    @Test
    void shouldRejectZeroBetAmount() {
        when(gameRepository.findByIdForShare(gameId)).thenReturn(Optional.of(game));

        assertThatThrownBy(() ->
                betService.placeBet(gameId, Symbol.TIGER, BigDecimal.ZERO))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "BET_AMOUNT_INVALID");

        verifyNoInteractions(
                playerRepository, betRepository,
                walletTransactionRepository, eventPublisher);
    }
}