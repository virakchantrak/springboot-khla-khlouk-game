package kh.virakchantrak.KhlaKhlouk.player.controller.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreatePlayerRequest(

        @NotBlank(message = "Username is required")
        @Size(max = 100, message = "Username must not exceed 100 characters")
        String username,

        @NotBlank(message = "Password is required")
        @Size(min = 6, message = "Password must be at least 6 characters long")
        String password,

        @NotNull(message = "Initial balance is required")
        @DecimalMin(
                value = "0.00",
                message = "Initial balance cannot be negative"
        )
        BigDecimal initialBalance
) {
}
