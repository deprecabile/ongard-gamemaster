package com.ondgard.game.chat.service.agent.setup;

import com.google.gson.Gson;
import com.ondgard.game.GameGsonFactory;
import com.ondgard.game.chat.service.rag.LoreRetrievalService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.Map;

public class CampaignSetupAdvisorTools {

  private static final Gson GSON = GameGsonFactory.build();

  private final LoreRetrievalService loreRetrievalService;
  private final String loreLangCode;

  public CampaignSetupAdvisorTools(LoreRetrievalService loreRetrievalService,
                                   String loreLangCode) {
    this.loreRetrievalService = loreRetrievalService;
    this.loreLangCode = loreLangCode;
  }

  @Tool( description = "Search the world lore knowledge base. Use keyword/noun phrases as query, not full sentences." )
  public String searchLore(
      @ToolParam( description = "Search query — keywords or noun phrases" ) String query) {
    String result = loreRetrievalService.retrieveForAdvisor(loreLangCode, query);
    return GSON.toJson(Map.of("lore", result));
  }
}
