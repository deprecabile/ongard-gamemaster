package com.ondgard.game.chat.repository.projection;

import java.time.LocalDateTime;

public record AdvisorLogProjection(
    int turnNumber,
    String userMessage,
    String advisorResponse,
    LocalDateTime created
) {
}
