package com.ondgard.game.chat.contract;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CampaignInitRequest(
    @JsonProperty( required = true ) String characterHash,
    @JsonProperty( required = true ) String initialContext
) {
}
