package com.ondgard.game.chat.model;

import java.io.Serializable;

public record CampaignQuestLog(
    int turnNumber,
    String questActive,
    String questCompleted
) implements Serializable {
}
