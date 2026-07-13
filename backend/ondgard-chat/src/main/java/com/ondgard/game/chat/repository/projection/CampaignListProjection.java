package com.ondgard.game.chat.repository.projection;

import java.time.LocalDateTime;

public record CampaignListProjection(
    Long campaignId,
    String characterHash,
    String characterName,
    String raceCode,
    int turnCount,
    String currentLocation,
    LocalDateTime updated,
    String narrativeSummary,
    String description
) {
}
