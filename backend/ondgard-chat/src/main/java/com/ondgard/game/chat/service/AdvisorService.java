package com.ondgard.game.chat.service;

import com.ondgard.game.chat.config.PipelineProperties;
import com.ondgard.game.chat.config.ai.TokenTrackingContext;
import com.ondgard.game.chat.contract.sse.SseProgressCode;
import com.ondgard.game.chat.contract.sse.campaign.ask.SseAdvisorCompletedEvent;
import com.ondgard.game.chat.contract.sse.campaign.ask.SseAdvisorErrorEvent;
import com.ondgard.game.chat.contract.sse.campaign.ask.SseAdvisorEventType;
import com.ondgard.game.chat.contract.sse.campaign.ask.SseAdvisorStartEvent;
import com.ondgard.game.chat.model.InteractionContext;
import com.ondgard.game.chat.repository.projection.AdvisorLogProjection;
import com.ondgard.game.chat.service.agent.advisor.AdvisorAgent;
import com.ondgard.game.chat.service.agent.advisor.AdvisorTools;
import com.ondgard.game.chat.service.rag.LoreRetrievalService;
import com.ondgard.game.header.GameUserHeader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.context.annotation.RequestScope;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

@Slf4j
@Service
@RequestScope
@RequiredArgsConstructor
public class AdvisorService {

  @Qualifier( "parallelExecutor" ) private final ExecutorService parallelExecutor;
  private final AdvisorAgent advisorAgent;
  private final AdvisorLogService advisorLogService;
  private final CampaignService campaignService;
  private final LoreRetrievalService loreRetrievalService;
  private final PipelineProperties pipelineProps;

  public SseEmitter processAsk(GameUserHeader userHeader, InteractionContext request) {
    final SseEmitter emitter = new SseEmitter(pipelineProps.getSseTimeout().toMillis());
    final String username = userHeader.getUsername();

    CompletableFuture.runAsync(TokenTrackingContext.wrap(() -> {
      try{
        // 1. STARTED
        sendEvent(emitter, SseAdvisorEventType.STARTED, new SseAdvisorStartEvent(true));

        // 2. Caricamento ask history per il prompt
        Long campaignId = request.getCampaignContext().getCampaignId();
        int currentTurn = request.getCampaignContext().getCurrentTurn();
        Collection<AdvisorLogProjection> history = advisorLogService.findForPrompt(campaignId, currentTurn);

        // 3. Creazione tools per l'Advisor
        AdvisorTools advisorTools = new AdvisorTools(
            loreRetrievalService, request.getCampaignContext(), request.getLoreLangCode());

        // 4. Chiamata Advisor con heartbeat
        String advisorOutput;
        try( var hb = new SseHeartbeat(emitter, 10, SseAdvisorEventType.THINKING,
            SseProgressCode.ADVISOR_THINKING) ){
          advisorOutput = advisorAgent.answer(
              request.getOutputLang(), request.getLoreLangCode(),
              history, request.getUserMessage(), advisorTools);
        }

        // 5. Propagazione advisor facts
        List<String> facts = advisorTools.getAdvisorFacts();
        if( !facts.isEmpty() ){
          request.getCampaignContext().getAdvisorFacts().addAll(facts);
          campaignService.saveContextOnly(request.getCampaignContext(), request.getCharacter().getCharacterHash());
          log.info("Propagated {} advisor facts for character {}", facts.size(), request.getCharacter().getCharacterHash());
        }

        // 7. Persistenza (fire-and-forget)
        advisorLogService.persistAsync(campaignId, currentTurn, request.getUserMessage(), advisorOutput);

        // 8. COMPLETED
        sendEvent(emitter, SseAdvisorEventType.COMPLETED, new SseAdvisorCompletedEvent(LocalDateTime.now(), advisorOutput));
        emitter.complete();
      }catch(Exception ex){
        handleError(emitter, username, ex);
      }
    }), parallelExecutor);

    emitter.onTimeout(() -> log.warn("SSE advisor timeout for user: {}", username));
    emitter.onError(ex -> log.error("SSE advisor error for user: {}", username, ex));

    return emitter;
  }

  // --- private ---

  private void sendEvent(SseEmitter emitter, SseAdvisorEventType type, Object data) throws IOException {
    emitter.send(SseEmitter.event().name(type.getValue()).data(data, MediaType.APPLICATION_JSON));
  }

  private void handleError(SseEmitter emitter, String username, Exception ex) {
    if( isClientDisconnected(ex) ){
      log.warn("Client disconnected during advisor pipeline for user: {}", username);
      return;
    }
    log.error("Error during advisor pipeline for user: {}", username, ex);
    try{
      sendEvent(emitter, SseAdvisorEventType.ERROR,
          new SseAdvisorErrorEvent(LocalDateTime.now(), "GC_500_03", ex.getMessage()));
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
