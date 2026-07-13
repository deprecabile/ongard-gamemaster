package com.ondgard.game.chat.model.agent;

import com.ondgard.game.postprocessing.LlmRequired;
import com.ondgard.game.postprocessing.OndgardLlmSchema;

import java.util.List;

@OndgardLlmSchema( "RAG_QUERY" )
public record RagQueryResponse(
    @LlmRequired List<String> queries
) {
}
