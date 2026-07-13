package com.ondgard.game.chat.service.agent.advisor;

import com.google.gson.Gson;
import com.ondgard.game.GameGsonFactory;
import com.ondgard.game.chat.model.CampaignContext;
import com.ondgard.game.chat.model.GameScene;
import com.ondgard.game.chat.model.inventory.Inventory;
import com.ondgard.game.chat.service.rag.LoreRetrievalService;
import com.ondgard.game.chat.util.ChatEntryFormatter;
import lombok.Getter;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AdvisorTools {

  private static final Gson GSON = GameGsonFactory.build();

  private final LoreRetrievalService loreRetrievalService;
  private final CampaignContext campaignContext;
  private final String loreLangCode;

  @Getter
  private final List<String> advisorFacts = new ArrayList<>();

  public AdvisorTools(LoreRetrievalService loreRetrievalService,
                      CampaignContext campaignContext,
                      String loreLangCode) {
    this.loreRetrievalService = loreRetrievalService;
    this.campaignContext = campaignContext;
    this.loreLangCode = loreLangCode;
  }

  @Tool( description = "Search the world lore knowledge base. Use keyword/noun phrases as query, not full sentences." )
  public String searchLore(@ToolParam( description = "Search query — keywords or noun phrases" ) String query) {
    String result = loreRetrievalService.retrieveForAdvisor(loreLangCode, query);
    return toJson(Map.of("lore", result));
  }

  @Tool( description = "Get the current game scene, active quests, and player inventory." )
  public String getCurrentScene() {
    GameScene scene = campaignContext.getScene();
    Inventory inventory = campaignContext.getInventory();
    String questActive = campaignContext.getQuestLog() != null
        && campaignContext.getQuestLog().questActive() != null
        && !campaignContext.getQuestLog().questActive().isBlank()
        ? campaignContext.getQuestLog().questActive() : null;

    return GSON.toJson(new CurrentScene(scene, inventory, questActive));
  }

  @Tool( description = "Get recent conversation turns between the player and the Game Master." )
  public String getRecentHistory() {
    String history = ChatEntryFormatter.toTextBuffer(campaignContext.getRecentHistory());
    return toJson(Map.of("history", history));
  }

  @Tool( description = "Search past narrative events. Returns the narrative summary and uncompressed recent turns." )
  public String searchPastEvents(@ToolParam( description = "Search query about past events" ) String query) {
    String narrativeSummary = null;
    String recentUncompressedTurns = null;

    if( campaignContext.getNarrativeSummary() != null && !campaignContext.getNarrativeSummary().isBlank() ){
      narrativeSummary = campaignContext.getNarrativeSummary();
    }
    if( campaignContext.getRawSummaryBuffer() != null && !campaignContext.getRawSummaryBuffer().isEmpty() ){
      recentUncompressedTurns = ChatEntryFormatter.toTextBuffer(campaignContext.getRawSummaryBuffer());
    }

    return GSON.toJson(new PastEvents(narrativeSummary, recentUncompressedTurns));
  }

  @Tool( description = "Register a fact you invented (e.g. an NPC name, a detail about a place) so the Game Master will maintain consistency. Use sparingly — only for genuinely useful new information." )
  public String registerAdvisorFact(@ToolParam( description = "The fact to register for the Game Master" ) String fact) {
    advisorFacts.add(fact);
    return "{\"status\":\"registered\"}";
  }

  private static String toJson(Map<String, ?> data) {
    return GSON.toJson(data);
  }

  private record CurrentScene( GameScene scene, Inventory inventory, String questActive ) {
  }

  private record PastEvents( String narrativeSummary, String recentUncompressedTurns ) {
  }
}
