package com.ondgard.game.chat.contract.campaign.setup;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ondgard.game.chat.model.setup.SetupGenerateMode;

public record SetupGenerateRequest(
    SetupGenerateMode mode,
    @JsonProperty( required = true ) String archetypeCode,
    String characterPrompt,
    String raceCode,
    String characterName
) {
  public SetupGenerateRequest {
    if( mode == null ) mode = SetupGenerateMode.FULL;
  }
}
