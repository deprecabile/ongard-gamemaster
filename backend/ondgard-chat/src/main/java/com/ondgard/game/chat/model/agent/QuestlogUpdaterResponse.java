package com.ondgard.game.chat.model.agent;

import com.ondgard.game.postprocessing.LlmRequired;
import com.ondgard.game.postprocessing.OndgardLlmSchema;

@OndgardLlmSchema( "QUESTLOG" )
public record QuestlogUpdaterResponse(
    @LlmRequired String questActive,
    @LlmRequired String questCompleted
) {
}
