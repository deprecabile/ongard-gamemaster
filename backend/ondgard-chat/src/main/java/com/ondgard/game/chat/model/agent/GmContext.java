package com.ondgard.game.chat.model.agent;

import com.ondgard.game.chat.model.CampaignContext;
import com.ondgard.game.chat.model.PlayerCharacter;

public record GmContext(
    String language,
    PlayerCharacter character,
    String loreSection,
    CampaignContext campaignContext,
    String userAction
) {
}
