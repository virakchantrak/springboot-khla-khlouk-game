package kh.virakchantrak.KhlaKhlouk.game.domain;

import org.springframework.stereotype.Component;

import java.util.Random;

@Component
public class Dice {

    private final Random random;

    public Dice() {
        this(new Random());
    }

    Dice(Random random) {
        this.random = random;
    }

    public Symbol roll() {
        Symbol[] symbols = Symbol.values();
        return symbols[random.nextInt(symbols.length)];
    }

    public RollResult rollThree() {
        return new RollResult(
                roll(),
                roll(),
                roll()
        );
    }
}
