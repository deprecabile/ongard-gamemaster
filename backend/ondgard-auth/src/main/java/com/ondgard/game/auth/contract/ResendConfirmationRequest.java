package com.ondgard.game.auth.contract;

import jakarta.validation.constraints.NotBlank;

public record ResendConfirmationRequest(
    @NotBlank String username
) {
}
