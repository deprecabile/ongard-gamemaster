package com.ondgard.game.chat.contract;

import java.time.LocalDateTime;

public record AdvisorLogResponse(
    int turnNumber,
    String userMessage,
    String advisorResponse,
    LocalDateTime created
) {
}
