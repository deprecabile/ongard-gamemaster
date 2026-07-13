package com.ondgard.game.chat.service.campaign.setup;

import com.ondgard.game.chat.client.AccountClient;
import com.ondgard.game.chat.config.PipelineProperties;
import com.ondgard.game.chat.contract.campaign.setup.SetupInteractionRequest;
import com.ondgard.game.chat.contract.sse.SseProgressCode;
import com.ondgard.game.chat.contract.sse.campaign.setup.SseSetupInteractionCompletedEvent;
import com.ondgard.game.chat.contract.sse.campaign.setup.SseSetupInteractionErrorEvent;
import com.ondgard.game.chat.contract.sse.campaign.setup.SseSetupInteractionEventType;
import com.ondgard.game.chat.contract.sse.campaign.setup.SseSetupInteractionStartEvent;
import com.ondgard.game.chat.error.GameErrorCode;
import com.ondgard.game.chat.model.ChatEntry;
import com.ondgard.game.chat.service.SseHeartbeat;
import com.ondgard.game.chat.service.agent.setup.CampaignSetupAdvisorAgent;
import com.ondgard.game.chat.service.agent.setup.CampaignSetupAgentsFactory;
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
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

@Slf4j
@Service
@RequiredArgsConstructor
public class CampaignSetupInteractionService {

  @Qualifier( "parallelExecutor" ) private final ExecutorService parallelExecutor;
  private final CampaignSetupSessionService sessionService;
  private final CampaignSetupAgentsFactory agentsFactory;
  private final PipelineProperties pipelineProps;
  private final RagReadinessGate ragReadinessGate;
  private final AccountClient accountClient;

  public SseEmitter interact(GameUserHeader userHeader, SetupInteractionRequest request) {
    // Synchronous pre-checks
    if( !ragReadinessGate.isReady() ){
      throw new ServiceUnavailableException(
          GameErrorCode.RAG_NOT_READY.getCode(),
          "Il mondo di Ondgard si sta materializzando...");
    }

    CheckLimitResponse limitCheck = accountClient.checkLimit(userHeader.getUserId());
    if( limitCheck.exceeded() ){
      throw new TokenLimitExceededException(GameErrorCode.TOKEN_LIMIT_EXCEEDED.getCode(), limitCheck);
    }

    var emitter = new SseEmitter(pipelineProps.getSseTimeout().toMillis());

    CompletableFuture.runAsync(() -> runPipeline(emitter, userHeader, request), parallelExecutor);

    emitter.onTimeout(() -> log.warn("SSE timeout for setup interaction: userId={}", userHeader.getUserId()));
    emitter.onError(ex -> log.error("SSE error for setup interaction: userId={}", userHeader.getUserId(), ex));

    return emitter;
  }

  // ========================= private =========================

  private void runPipeline(SseEmitter emitter, GameUserHeader userHeader, SetupInteractionRequest request) {
    try{
      sendEvent(emitter, SseSetupInteractionEventType.STARTED, new SseSetupInteractionStartEvent(true));

      sessionService.getOrCreate(userHeader.getUserId());

      Collection<ChatEntry> history = sessionService.getHistory(userHeader.getUserId());

      CampaignSetupAdvisorAgent advisorAgent = agentsFactory.buildAdvisorAgent(
          userHeader.getLanguage(),
          request.characterPrompt(),
          request.startingSituation(),
          request.raceCode(),
          request.characterName());

      String advisorOutput;
      try( var hb = new SseHeartbeat(emitter, 10,
          SseSetupInteractionEventType.THINKING.getValue(),
          SseProgressCode.SETUP_ADVISOR_THINKING) ){
        advisorOutput = advisorAgent.chat(history, request.message());
      }

      sessionService.addMessage(userHeader.getUserId(), new ChatEntry(0, request.message(), advisorOutput));

      sendEvent(emitter, SseSetupInteractionEventType.COMPLETED, new SseSetupInteractionCompletedEvent(LocalDateTime.now(), advisorOutput));
      emitter.complete();
    }catch(Exception ex){
      handleError(emitter, userHeader.getUserId(), ex);
    }
  }

  private void sendEvent(SseEmitter emitter, SseSetupInteractionEventType type, Object data) throws IOException {
    emitter.send(SseEmitter.event().name(type.getValue()).data(data, MediaType.APPLICATION_JSON));
  }

  private void handleError(SseEmitter emitter, String userId, Exception ex) {
    if( isClientDisconnected(ex) ){
      log.warn("Client disconnected during setup interaction for userId={}", userId);
      return;
    }
    log.error("Error during setup interaction for userId={}", userId, ex);
    try{
      sendEvent(emitter, SseSetupInteractionEventType.ERROR,
          new SseSetupInteractionErrorEvent(LocalDateTime.now(), "GC_500_05", ex.getMessage()));
    }catch(IOException ignored){
    }
    emitter.completeWithError(ex);
  }

  private static boolean isClientDisconnected(Throwable ex) {
    while( ex != null ){
      if( ex instanceof AsyncRequestNotUsableException ) return true;
      ex = ex.getCause();
    }
    return false;
  }
}
