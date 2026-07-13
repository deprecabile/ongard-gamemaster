package com.ondgard.game.auth.contract;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ConfirmRequest(
    @NotNull UUID token
) {
}
