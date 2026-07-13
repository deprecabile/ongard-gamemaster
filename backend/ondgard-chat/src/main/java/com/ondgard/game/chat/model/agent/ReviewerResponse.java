package com.ondgard.game.chat.model.agent;

import com.ondgard.game.postprocessing.LlmRequired;
import com.ondgard.game.postprocessing.OndgardLlmSchema;

@OndgardLlmSchema( "LORE_REVIEWER" )
public record ReviewerResponse(
    @LlmRequired boolean isPass,
    @LlmRequired String category,
    @LlmRequired String feedbackReason
) {
}
