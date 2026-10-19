package kh.virakchantrak.KhlaKhlouk.player.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreatePlayerRequest(

        @NotBlank(message = "Username is required")
        @Pattern(
                regexp = "^[A-Za-z0-9_.-]{3,50}$",
                message = "Username must be 3-50 characters: letters, digits, underscore, dot or hyphen"
        )
        String username,

        @NotBlank(message = "Password is required")
        @Size(min = 6, message = "Password must be at least 6 characters long")
        String password
) {
}
