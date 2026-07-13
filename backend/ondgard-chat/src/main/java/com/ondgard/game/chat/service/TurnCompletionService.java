package com.ondgard.game.chat.service;

import com.ondgard.game.chat.config.SummaryProperties;
import com.ondgard.game.chat.config.ai.TokenTrackingContext;
import com.ondgard.game.chat.model.*;
import com.ondgard.game.chat.model.agent.QuestlogUpdaterResponse;
import com.ondgard.game.chat.model.inventory.Inventory;
import com.ondgard.game.chat.service.agent.TurnAgentsFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;

@Slf4j
@Service
@RequiredArgsConstructor
public class TurnCompletionService {

  private final CampaignService campaignService;
  private final CampaignSnapshotService campaignSnapshotService;
  private final AdventureLogService adventureLogService;
  private final SummaryService summaryService;
  private final SummaryProperties summaryProps;
  @Qualifier( "parallelExecutor" ) private final ExecutorService parallelExecutor;

  public void completeTurn(InteractionContext request, TurnAgentsFactory agents, String draft) {
    final CampaignContext campaignContext = request.getCampaignContext();
    final int newTurn = campaignContext.getCurrentTurn() + 1;
    campaignContext.setCurrentTurn(newTurn);
    campaignContext.setTurnsSinceLastSummary(campaignContext.getTurnsSinceLastSummary() + 1);

    // --- PARALLEL UPDATERS (inventory + questlog + scene) ---
    runParallelUpdaters(agents, campaignContext, draft, newTurn, request.getUserMessage());

    // --- CHAT ENTRY + ADVENTURE LOG ---
    final ChatEntry chatEntry = new ChatEntry(newTurn, request.getUserMessage(), draft);
    campaignContext.getRecentHistory().add(chatEntry);
    adventureLogService.persistTurnAsync(campaignContext.getCampaignId(), chatEntry);

    // --- SLIDING WINDOW + SUMMARY BUFFER ---
    manageHistoryWindow(campaignContext);

    // --- TRIGGER SUMMARY ---
    triggerSummaryIfNeeded(request, campaignContext);

    // --- SAVE ---
    campaignService.save(campaignContext, request.getCharacter().getCharacterHash());

    log.info("Turn {} completed for character {} ({})", newTurn, request.getCharacter().getName(), request.getCharacter().getCharacterHash());
  }

  // ************************************ private ************************************

  private void runParallelUpdaters(TurnAgentsFactory agents, CampaignContext campaignContext, String draft, int newTurn, String userMessage) {
    Function<String, Inventory> invUpdater = agents.buildInventoryUpdater(campaignContext.getInventory(), userMessage);
    var questUpdater = agents.buildQuestlogUpdater(campaignContext.getQuestLog(), campaignContext.getScene());
    Function<String, GameScene> sceneUpdater = agents.buildSceneUpdater(campaignContext.getScene());

    CompletableFuture<Inventory> inventoryFuture = CompletableFuture.supplyAsync(
        TokenTrackingContext.wrap(() -> invUpdater.apply(draft)), parallelExecutor);
    CompletableFuture<QuestlogUpdaterResponse> questResponseFuture = CompletableFuture.supplyAsync(
        TokenTrackingContext.wrap(() -> questUpdater.apply(draft)), parallelExecutor);
    CompletableFuture<GameScene> sceneFuture = CompletableFuture.supplyAsync(
        TokenTrackingContext.wrap(() -> sceneUpdater.apply(draft)), parallelExecutor);

    CompletableFuture.allOf(inventoryFuture, questResponseFuture, sceneFuture).join();

    Inventory updatedInventory = inventoryFuture.join();
    QuestlogUpdaterResponse questResponse = questResponseFuture.join();
    campaignContext.setScene(sceneFuture.join());

    boolean inventoryChanged = !updatedInventory.equals(campaignContext.getInventory());
    if( inventoryChanged ){
      campaignContext.setInventory(updatedInventory);
      campaignSnapshotService.persistInventoryAsync(campaignContext.getCampaignId(), newTurn, updatedInventory);
    }

    CampaignQuestLog currentQuestLog = campaignContext.getQuestLog();
    boolean questChanged = !Objects.equals(questResponse.questActive(), currentQuestLog.questActive())
        || !Objects.equals(questResponse.questCompleted(), currentQuestLog.questCompleted());
    if( questChanged ){
      CampaignQuestLog updatedQuestLog = new CampaignQuestLog(newTurn, questResponse.questActive(), questResponse.questCompleted());
      campaignContext.setQuestLog(updatedQuestLog);
      campaignSnapshotService.persistQuestlogAsync(campaignContext.getCampaignId(), newTurn, updatedQuestLog);
    }
  }

  private void manageHistoryWindow(CampaignContext campaignContext) {
    while( campaignContext.getRecentHistory().size() > summaryProps.getRecentHistorySize() ){
      ChatEntry evicted = campaignContext.getRecentHistory().removeFirst();
      List<ChatEntry> buf = campaignContext.getRawSummaryBuffer();
      if( buf == null ){
        buf = new ArrayList<>();
        campaignContext.setRawSummaryBuffer(buf);
      }
      buf.add(evicted);
    }
  }

  private void triggerSummaryIfNeeded(InteractionContext request, CampaignContext campaignContext) {
    boolean firstCycle = campaignContext.getSummaryVersion() == 0;
    int threshold = firstCycle
        ? summaryProps.getTriggerEveryNTurns() * 2
        : summaryProps.getTriggerEveryNTurns();

    if( campaignContext.getTurnsSinceLastSummary() >= threshold ){
      List<ChatEntry> buffer = campaignContext.getRawSummaryBuffer();
      if( buffer != null && !buffer.isEmpty() ){
        log.debug("triggering summary for character {} ({})", request.getCharacter().getName(), request.getCharacter().getCharacterHash());
        summaryService.generateAsync(
            request.getOutputLang(),
            buffer,
            campaignContext.getNarrativeSummary(),
            campaignContext.getSummaryVersion() + 1,
            campaignContext.getCampaignId(),
            request.getCharacter().getCharacterHash());
      }
      campaignContext.setRawSummaryBuffer(new ArrayList<>());
      campaignContext.setTurnsSinceLastSummary(0);
    }
  }
}
