package com.ondgard.game.chat.contract.campaign.setup;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SetupInteractionRequest(
    @JsonProperty( required = true ) String message,
    String characterPrompt,
    String startingSituation,
    String raceCode,
    String characterName
) {
}
