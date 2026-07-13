package com.ondgard.game.auth.contract.google;

public record GoogleRegistrationRequiredResponse(
    String email,
    String suggestedUsername
) {
}
