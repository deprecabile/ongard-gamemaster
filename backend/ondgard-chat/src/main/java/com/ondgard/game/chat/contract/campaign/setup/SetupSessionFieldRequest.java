package com.ondgard.game.chat.contract.campaign.setup;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SetupSessionFieldRequest(
    @JsonProperty( required = true ) String value
) {
}
