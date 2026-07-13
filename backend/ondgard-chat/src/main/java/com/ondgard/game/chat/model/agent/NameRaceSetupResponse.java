package com.ondgard.game.chat.model.agent;

import com.ondgard.game.postprocessing.LlmRequired;
import com.ondgard.game.postprocessing.OndgardLlmSchema;

@OndgardLlmSchema( "NAME_RACE_SETUP" )
public record NameRaceSetupResponse(
    @LlmRequired String raceCode,
    @LlmRequired String characterName
) {
}
