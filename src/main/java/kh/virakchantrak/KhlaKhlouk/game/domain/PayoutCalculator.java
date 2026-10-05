package kh.virakchantrak.KhlaKhlouk.game.domain;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class PayoutCalculator {

    public BigDecimal calculate(
            BigDecimal betAmount,
            long symbolCount
    ) {
        if (symbolCount == 0) {
            return BigDecimal.ZERO;
        }

        return betAmount.multiply(
                BigDecimal.valueOf(symbolCount + 1)
        );
    }
}
