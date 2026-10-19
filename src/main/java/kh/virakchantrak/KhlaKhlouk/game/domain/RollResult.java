package kh.virakchantrak.KhlaKhlouk.game.domain;

import java.util.stream.Stream;

public record RollResult(
        Symbol dice1,
        Symbol dice2,
        Symbol dice3
) {

    public long count(Symbol symbol) {
        return Stream.of(dice1, dice2, dice3)
                .filter(symbol::equals)
                .count();
    }

    public boolean contains(Symbol symbol) {
        return count(symbol) > 0;
    }
}
