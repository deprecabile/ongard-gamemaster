package com.ondgard.game.chat.service.agent;

import com.google.gson.Gson;
import com.ondgard.game.GameGsonFactory;
import com.ondgard.game.chat.model.CampaignContext;
import com.ondgard.game.chat.model.CampaignQuestLog;
import com.ondgard.game.chat.model.GameScene;
import com.ondgard.game.chat.model.PlayerCharacter;
import com.ondgard.game.chat.model.inventory.Inventory;
import com.ondgard.game.chat.util.ChatEntryFormatter;

class GmPromptBuilder {

  private static final Gson GSON = GameGsonFactory.build();

  static String buildCharacterSection(PlayerCharacter character) {
    return """
        # PERSONAGGIO DEL GIOCATORE
        - **Nome:** %s
        - **Razza:** %s — %s
        - **Descrizione:** %s""".formatted(
        character.getName(),
        character.getRace().getName(),
        character.getRace().getDescription(),
        character.getDescription()
    );
  }

  static String buildCampaignSection(CampaignContext ctx) {
    if( ctx == null ){
      return "";
    }

    var sb = new StringBuilder();

    // Scena corrente
    GameScene scene = ctx.getScene();
    boolean hasScene = scene != null && (scene.getCurrentLocation() != null || scene.getGameDate() != null
        || scene.getGameTime() != null || scene.getMeteo() != null || scene.getTemperature() != null);
    if( hasScene ){
      sb.append("\n\n## SCENA CORRENTE\n");
      if( scene.getCurrentLocation() != null )
        sb.append("- **Luogo:** ").append(scene.getCurrentLocation()).append("\n");
      if( scene.getGameDate() != null ) sb.append("- **Data:** ").append(scene.getGameDate()).append("\n");
      if( scene.getGameTime() != null ) sb.append("- **Ora:** ").append(scene.getGameTime()).append("\n");
      if( scene.getMeteo() != null ) sb.append("- **Meteo:** ").append(scene.getMeteo()).append("\n");
      if( scene.getTemperature() != null ) sb.append("- **Temperatura:** ").append(scene.getTemperature()).append("\n");
    }

    // Riassunto narrativo
    if( ctx.getNarrativeSummary() != null && !ctx.getNarrativeSummary().isBlank() ){
      sb.append("\n\n## RIASSUNTO NARRATIVO\n").append(ctx.getNarrativeSummary()).append("\n");
    }

    // Buffer raw (turni non ancora compressi)
    if( ctx.getRawSummaryBuffer() != null && !ctx.getRawSummaryBuffer().isEmpty() ){
      sb.append("\n\n## EVENTI RECENTI NON RIASSUNTI\n").append(ChatEntryFormatter.toTextBuffer(ctx.getRawSummaryBuffer()));
    }

    // Storia recente
    if( ctx.getRecentHistory() != null && !ctx.getRecentHistory().isEmpty() ){
      sb.append("\n\n## STORIA RECENTE\n").append(ChatEntryFormatter.toTextBuffer(ctx.getRecentHistory()));
    }

    // Inventario
    if( ctx.getInventory() != null ){
      sb.append(buildInventorySection(ctx.getInventory()));
    }

    // Quest attive
    if( ctx.getQuestLog() != null ){
      sb.append(buildQuestLogSection(ctx.getQuestLog()));
    }

    return sb.toString();
  }

  static String buildAdvisorFactsSection(CampaignContext ctx) {
    if( ctx == null || ctx.getAdvisorFacts().isEmpty() ){
      return "";
    }
    var sb = new StringBuilder("## FATTI STABILITI DALL'ADVISOR\n\n");
    sb.append("I seguenti dettagli sono stati comunicati al giocatore dall'Advisor. ");
    sb.append("Trattali come fatti stabiliti e mantieni coerenza nella narrazione.\n\n");
    for( String fact : ctx.getAdvisorFacts() ){
      sb.append("- ").append(fact).append("\n");
    }
    return sb.toString();
  }

  private static String buildInventorySection(Inventory inventory) {
    String json = GSON.toJson(inventory);
    return "\n\n## INVENTARIO DEL GIOCATORE\n```json\n" + json + "\n```\n";
  }

  private static String buildQuestLogSection(CampaignQuestLog questLog) {
    var sb = new StringBuilder();

    if( questLog.questActive() != null && !questLog.questActive().isBlank() ){
      sb.append("\n\n## QUEST ATTIVE\n").append(questLog.questActive()).append("\n");
    }

    return sb.toString();
  }
}
