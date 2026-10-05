package kh.virakchantrak.KhlaKhlouk;

import kh.virakchantrak.KhlaKhlouk.game.domain.PayoutCalculator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PayoutCalculatorTest {

    private final PayoutCalculator calculator =
            new PayoutCalculator();

    @Test
    void shouldReturnZeroWhenNoMatch() {

        BigDecimal payout = calculator.calculate(
                BigDecimal.TEN,
                0
        );

        assertEquals(
                BigDecimal.ZERO,
                payout
        );
    }

    @Test
    void shouldReturnTwoTimesForOneMatch() {

        BigDecimal payout = calculator.calculate(
                BigDecimal.TEN,
                1
        );

        assertEquals(
                new BigDecimal("20"),
                payout
        );
    }

    @Test
    void shouldReturnThreeTimesForTwoMatches() {

        BigDecimal payout = calculator.calculate(
                BigDecimal.TEN,
                2
        );

        assertEquals(
                new BigDecimal("30"),
                payout
        );
    }

    @Test
    void shouldReturnFourTimesForThreeMatches() {

        BigDecimal payout = calculator.calculate(
                BigDecimal.TEN,
                3
        );

        assertEquals(
                new BigDecimal("40"),
                payout
        );
    }
}
