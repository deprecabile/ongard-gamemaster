package com.ondgard.game.chat.model.setup;

import java.io.Serializable;
import java.util.List;

public record CampaignArchetype(
    String code,
    String description,
    String theme,
    List<String> inventoryHints
) implements Serializable {
}
