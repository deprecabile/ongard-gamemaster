package com.ongard.game.chat.repository.projection;

import java.time.LocalDateTime;
import java.util.UUID;

public record PlayerCharacterProjection(
    String characterHash,
    UUID userHash,
    String raceCode,
    String name,
    String description,
    LocalDateTime created
) {
}
