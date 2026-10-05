package kh.virakchantrak.KhlaKhlouk;

import kh.virakchantrak.KhlaKhlouk.game.domain.RollResult;
import kh.virakchantrak.KhlaKhlouk.game.domain.Symbol;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RollResultTest {

    @Test
    void shouldCountMatchingSymbols() {

        RollResult result = new RollResult(
                Symbol.TIGER,
                Symbol.CRAB,
                Symbol.TIGER
        );

        assertEquals(2, result.count(Symbol.TIGER));
        assertEquals(1, result.count(Symbol.CRAB));
        assertEquals(0, result.count(Symbol.FISH));
    }
}
