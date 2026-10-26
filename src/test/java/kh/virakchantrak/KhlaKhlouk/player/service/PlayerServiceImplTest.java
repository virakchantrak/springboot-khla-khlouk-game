package kh.virakchantrak.KhlaKhlouk.player.service;

import kh.virakchantrak.KhlaKhlouk.common.exception.BusinessException;
import kh.virakchantrak.KhlaKhlouk.player.domain.Player;
import kh.virakchantrak.KhlaKhlouk.player.repository.PlayerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlayerServiceImplTest {

    @Mock PlayerRepository playerRepository;
    @Mock PasswordEncoder passwordEncoder;

    @InjectMocks PlayerServiceImpl playerService;

    @BeforeEach
    void setUp() {
        // the @Value setting is not loaded in a plain unit test, so set it by hand
        ReflectionTestUtils.setField(
                playerService, "startingBalance", new BigDecimal("1000.00"));
    }

    @Test
    void shouldRejectPasswordLongerThanBcryptLimit() {
        String tooLong = "a".repeat(73);

        assertThatThrownBy(() -> playerService.createPlayer("alice", tooLong))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "PASSWORD_TOO_LONG");

        verifyNoInteractions(playerRepository, passwordEncoder);
    }

    @Test
    void shouldCreatePlayerWithStartingBalanceAndHashedPassword() {
        when(playerRepository.findByUsernameIgnoreCase("alice")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("secret123")).thenReturn("hashed-secret");
        when(playerRepository.save(any(Player.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Player created = playerService.createPlayer("alice", "secret123");

        ArgumentCaptor<Player> captor = ArgumentCaptor.forClass(Player.class);
        verify(playerRepository).save(captor.capture());
        Player saved = captor.getValue();

        assertThat(created).isSameAs(saved);
        assertThat(saved.getUsername()).isEqualTo("alice");
        assertThat(saved.getBalance()).isEqualByComparingTo("1000.00");
        assertThat(saved.getPasswordHash()).isEqualTo("hashed-secret");
        assertThat(saved.getPasswordHash()).isNotEqualTo("secret123");
    }

    @Test
    void shouldRejectDuplicateUsername() {
        when(playerRepository.findByUsernameIgnoreCase("alice"))
                .thenReturn(Optional.of(new Player()));

        assertThatThrownBy(() -> playerService.createPlayer("alice", "secret123"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "USERNAME_ALREADY_EXISTS");

        verify(playerRepository, never()).save(any());
    }
}