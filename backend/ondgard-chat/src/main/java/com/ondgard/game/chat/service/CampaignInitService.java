package com.ondgard.game.chat.service;

import com.ondgard.game.chat.client.AccountClient;
import com.ondgard.game.chat.config.PipelineProperties;
import com.ondgard.game.chat.config.ai.TokenTrackingContext;
import com.ondgard.game.chat.contract.CampaignInitRequest;
import com.ondgard.game.chat.contract.sse.SseErrorEvent;
import com.ondgard.game.chat.contract.sse.SseProgressCode;
import com.ondgard.game.chat.contract.sse.campaign.init.SseCompletedInitCmpEvent;
import com.ondgard.game.chat.contract.sse.campaign.init.SseEventInitCmpType;
import com.ondgard.game.chat.contract.sse.campaign.init.SseProgressInitCmpEvent;
import com.ondgard.game.chat.contract.sse.campaign.init.SseStartInitCmpEvent;
import com.ondgard.game.chat.error.GameErrorCode;
import com.ondgard.game.chat.model.CampaignContext;
import com.ondgard.game.chat.model.CampaignQuestLog;
import com.ondgard.game.chat.model.GameScene;
import com.ondgard.game.chat.model.agent.QuestlogUpdaterResponse;
import com.ondgard.game.chat.model.inventory.Inventory;
import com.ondgard.game.chat.service.agent.MultilanguageTurnAgentsFactory;
import com.ondgard.game.chat.service.campaign.setup.CampaignSetupSessionService;
import com.ondgard.game.chat.service.rag.RagReadinessGate;
import com.ondgard.game.contract.account.CheckLimitResponse;
import com.ondgard.game.exception.ServiceUnavailableException;
import com.ondgard.game.exception.TokenLimitExceededException;
import com.ondgard.game.header.GameUserHeader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;

@Slf4j
@Service
@RequiredArgsConstructor
public class CampaignInitService {

  private final AccountClient accountClient;
  private final CampaignService campaignService;
  private final CharacterService characterService;
  private final MultilanguageTurnAgentsFactory turnAgentsFactory;
  private final CampaignSnapshotService snapshotService;
  private final PipelineProperties pipelineProps;
  @Qualifier( "parallelExecutor" ) private final ExecutorService parallelExecutor;
  private final CampaignSetupSessionService setupSessionService;
  private final RagReadinessGate ragReadinessGate;

  public SseEmitter initCampaign(GameUserHeader userHeader, CampaignInitRequest request) {
    if( !ragReadinessGate.isReady() ){
      throw new ServiceUnavailableException(GameErrorCode.RAG_NOT_READY.getCode(), "Il mondo di Ondgard si sta materializzando...");
    }

    CheckLimitResponse limitCheck = accountClient.checkLimit(userHeader.getUserId());
    if( limitCheck.exceeded() ){
      throw new TokenLimitExceededException(GameErrorCode.TOKEN_LIMIT_EXCEEDED.getCode(), limitCheck);
    }

    var emitter = new SseEmitter(pipelineProps.getSseTimeout().toMillis());

    // Validate character ownership (synchronous — fast)
    characterService.getCharacter(userHeader, request.characterHash());

    TokenTrackingContext.set(request.characterHash());
    try{
      CompletableFuture.runAsync(TokenTrackingContext.wrap(() -> {
        try{
          sendEvent(emitter, SseEventInitCmpType.STARTED, new SseStartInitCmpEvent(true));

          // Init token tracking on ondgard-account before creating the campaign
          sendProgress(emitter, SseProgressCode.CAMPAIGN_PREPARING);
          accountClient.initCampaignUsage(request.characterHash(), userHeader.getUserId());

          CampaignContext ctx = campaignService.load(
              UUID.fromString(userHeader.getUserId()), request.characterHash());

          // Run initializer agents in parallel with heartbeat
          sendProgress(emitter, SseProgressCode.INIT_AGENTS);

          var agents = turnAgentsFactory.forLanguage(userHeader.getLanguage());
          Function<String, Inventory> invInitializer = agents.buildInventoryInitializer();
          Function<String, QuestlogUpdaterResponse> questInitializer = agents.buildQuestlogInitializer();
          Function<String, GameScene> sceneInitializer = agents.buildSceneInitializer();

          Inventory inventory;
          QuestlogUpdaterResponse questResponse;
          GameScene scene;
          try( var hb = new SseHeartbeat(emitter, 10, SseProgressCode.AGENTS_WORKING) ){
            CompletableFuture<Inventory> invFuture = CompletableFuture.supplyAsync(
                TokenTrackingContext.wrap(() -> invInitializer.apply(request.initialContext())), parallelExecutor);
            CompletableFuture<QuestlogUpdaterResponse> questFuture = CompletableFuture.supplyAsync(
                TokenTrackingContext.wrap(() -> questInitializer.apply(request.initialContext())), parallelExecutor);
            CompletableFuture<GameScene> sceneFuture = CompletableFuture.supplyAsync(
                TokenTrackingContext.wrap(() -> sceneInitializer.apply(request.initialContext())), parallelExecutor);

            CompletableFuture.allOf(invFuture, questFuture, sceneFuture).join();

            inventory = invFuture.join();
            questResponse = questFuture.join();
            scene = sceneFuture.join();
          }

          // Update context
          sendProgress(emitter, SseProgressCode.CAMPAIGN_SAVING);
          ctx.setInventory(inventory);
          ctx.setScene(scene);
          String questActive = questResponse.questActive() != null ? questResponse.questActive() : "";
          String questCompleted = questResponse.questCompleted() != null ? questResponse.questCompleted() : "";
          CampaignQuestLog questLog = new CampaignQuestLog(0, questActive, questCompleted);
          ctx.setQuestLog(questLog);

          // Save to Redis
          campaignService.save(ctx, request.characterHash());

          // Persist to DB (async)
          snapshotService.persistInventoryAsync(ctx.getCampaignId(), 0, inventory);
          if( !questActive.isBlank() ){
            snapshotService.persistQuestlogAsync(ctx.getCampaignId(), 0, questLog);
          }

          log.info("Campaign initialized for characterHash={}", request.characterHash());

          deleteSetupCampaignSession(userHeader);
          sendEvent(emitter, SseEventInitCmpType.COMPLETED, new SseCompletedInitCmpEvent(LocalDateTime.now(), true));
          emitter.complete();
        }catch(Exception ex){
          handleError(emitter, request.characterHash(), ex);
        }
      }), parallelExecutor);
    }finally{
      TokenTrackingContext.clear();
    }

    emitter.onTimeout(() -> log.warn("SSE timeout for campaign init: characterHash={}", request.characterHash()));
    emitter.onError(ex -> log.error("SSE error for campaign init: characterHash={}", request.characterHash(), ex));

    return emitter;
  }

  private void deleteSetupCampaignSession(GameUserHeader userHeader) {
    // Cleanup setup session — campaign created, advisor session no longer needed
    try{
      setupSessionService.delete(userHeader.getUserId());
    }catch(Exception cleanupEx){
      log.warn("Failed to cleanup setup session for userId={}", userHeader.getUserId(), cleanupEx);
    }
  }

  private void sendProgress(SseEmitter emitter, SseProgressCode code) throws IOException {
    sendEvent(emitter, SseEventInitCmpType.PROGRESS, new SseProgressInitCmpEvent(LocalDateTime.now(), code.getValue()));
  }

  private void handleError(SseEmitter emitter, String characterHash, Exception ex) {
    if( isClientDisconnected(ex) ){
      log.warn("Client disconnected during campaign init for characterHash={}", characterHash);
      return;
    }
    log.error("Error during campaign init for characterHash={}", characterHash, ex);
    try{
      sendEvent(emitter, SseEventInitCmpType.ERROR, new SseErrorEvent(LocalDateTime.now(), "GC_500_02", ex.getMessage()));
    }catch(IOException ignored){
      // client already disconnected
    }
    emitter.completeWithError(ex);
  }

  private void sendEvent(SseEmitter emitter, SseEventInitCmpType type, Object data) throws IOException {
    emitter.send(SseEmitter.event().name(type.getValue()).data(data, MediaType.APPLICATION_JSON));
  }

  private static boolean isClientDisconnected(Throwable ex) {
    while( ex != null ){
      if( ex instanceof org.springframework.web.context.request.async.AsyncRequestNotUsableException ) return true;
      ex = ex.getCause();
    }
    return false;
  }
}
