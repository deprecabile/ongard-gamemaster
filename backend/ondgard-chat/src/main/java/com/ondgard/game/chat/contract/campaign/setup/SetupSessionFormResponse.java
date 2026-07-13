package com.ondgard.game.chat.contract.campaign.setup;

public record SetupSessionFormResponse(
    String raceCode,
    String characterName,
    String characterPrompt,
    String startingSituation,
    String archetypeCode
) {
}
