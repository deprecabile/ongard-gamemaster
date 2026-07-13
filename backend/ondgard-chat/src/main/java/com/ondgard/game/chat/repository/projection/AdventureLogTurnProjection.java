package com.ondgard.game.chat.repository.projection;

import java.time.LocalDateTime;

public record AdventureLogTurnProjection(
    int turnNumber,
    String userMessage,
    String gmResponse,
    LocalDateTime created
) {
}
