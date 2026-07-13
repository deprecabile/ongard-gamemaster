package com.ondgard.game.chat.service;

import com.ondgard.game.chat.config.PipelineProperties;
import com.ondgard.game.chat.config.ai.TokenTrackingContext;
import com.ondgard.game.chat.contract.sse.*;
import com.ondgard.game.chat.model.CampaignContext;
import com.ondgard.game.chat.model.ChatEntry;
import com.ondgard.game.chat.model.InteractionContext;
import com.ondgard.game.chat.model.agent.GmContext;
import com.ondgard.game.chat.model.agent.LoreValidationResult;
import com.ondgard.game.chat.model.agent.ReviewerResponse;
import com.ondgard.game.chat.model.inventory.Inventory;
import com.ondgard.game.chat.service.agent.*;
import com.ondgard.game.chat.service.rag.LoreRetrievalService;
import com.ondgard.game.header.GameUserHeader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.context.annotation.RequestScope;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequestScope
@RequiredArgsConstructor
public class GameMasterService {

  private final GmAgent gmAgent;
  private final RouterAgent routerAgent;
  private final LoreReviewerFactory loreReviewerFactory;
  private final MultilanguageTurnAgentsFactory turnAgentsFactory;
  private final LoreProvider loreProvider;
  private final LoreRetrievalService loreRetrievalService;
  private final TurnCompletionService turnCompletionService;
  private final PipelineProperties pipelineProps;
  @Qualifier( "parallelExecutor" ) private final ExecutorService parallelExecutor;

  public SseEmitter processAction(GameUserHeader userHeader, InteractionContext request) {
    final SseEmitter emitter = new SseEmitter(pipelineProps.getSseTimeout().toMillis());
    final String username = userHeader.getUsername();

    final String loreSection = resolveLore(request);
    final GmContext gmContext = new GmContext(request.getOutputLang(), request.getCharacter(),
        loreSection, request.getCampaignContext(), request.getUserMessage());

    CompletableFuture.runAsync(TokenTrackingContext.wrap(() -> {
      try{
        sendEvent(emitter, SseEventType.STARTED, new SseStartEvent(true));

        GenerationResult result = generateAndValidate(emitter, gmContext, request);

        // Consume advisor facts — already included in GM prompt, clear before save
        request.getCampaignContext().getAdvisorFacts().clear();

        log.info("gm response validated for character {} - running updaters", request.getCharacter().getName());
        sendProgress(emitter, SseProgressCode.UPDATERS_RUNNING);
        turnCompletionService.completeTurn(request, result.agents(), result.draft());

        sendEvent(emitter, SseEventType.COMPLETED, new SseCompletedEvent(LocalDateTime.now(), result.draft(), !result.validated()));
        emitter.complete();
      }catch(Exception ex){
        handleError(emitter, username, ex);
      }
    }), parallelExecutor);

    emitter.onTimeout(() -> log.warn("SSE timeout for user: {}", username));
    emitter.onError(ex -> log.error("SSE error for user: {}", username, ex));

    return emitter;
  }

  // ************************************ private ************************************

  private GenerationResult generateAndValidate(SseEmitter emitter, GmContext gmContext, InteractionContext request) throws IOException {
    String draft = null;
    boolean validated = false;
    String feedback = null;
    final TurnAgentsFactory agents = turnAgentsFactory.forLanguage(request.getOutputLangIsoCode());

    for( int attempt = 0; attempt < pipelineProps.getMaxAttempts(); attempt++ ){

      // --- GENERAZIONE ---
      if( attempt == 0 ){
        try( var hb = new SseHeartbeat(emitter, 10, SseProgressCode.GM_WRITING) ){
          log.info("GM generating response for character {} ({}) (attempt {})", request.getCharacter().getName(), request.getCharacter().getCharacterHash(), attempt + 1);
          draft = gmAgent.generate(gmContext);
          log.info("GM response generated for character {} ({})", request.getCharacter().getName(), request.getCharacter().getCharacterHash());
        }
      } else{
        sendProgress(emitter, SseProgressCode.GM_INCONSISTENCY);
        try( var hb = new SseHeartbeat(emitter, 10, SseProgressCode.GM_REWRITING) ){
          log.info("GM regenerating response for character {} ({}) (attempt {})", request.getCharacter().getName(), request.getCharacter().getCharacterHash(), attempt + 1);
          draft = gmAgent.regenerate(gmContext, draft, feedback);
          log.info("GM response regenerated for character {} ({})", request.getCharacter().getName(), request.getCharacter().getCharacterHash());
        }
      }

      // --- ROUTING ---
      sendProgress(emitter, SseProgressCode.VALIDATORS_RUNNING);
      Collection<String> themes = routerAgent.route(request.getLoreLangCode(), draft);

      // --- VALIDAZIONE PARALLELA (lore + inventario) ---
      LoreValidationResult result = validateParallel(request.getLoreLangCode(), agents, draft, themes,
          request.getCampaignContext().getInventory(), request.getCampaignContext().getRecentHistory());

      if( result.allPassed() ){
        validated = true;
        break;
      }

      feedback = result.aggregatedFeedback();
      log.info("Validation failed (attempt {}): {}", attempt + 1,
          result.failures().stream().map(r -> "{[" + r.category() + "] " + r.feedbackReason() + "}").collect(Collectors.joining(", "))
      );
    }

    if( !validated ){
      log.warn("Pipeline exhausted all {} attempts for character: {} ({}) — publishing with warning", pipelineProps.getMaxAttempts(), request.getCharacter().getName(), request.getCharacter().getCharacterHash());
    }

    return new GenerationResult(agents, draft, validated);
  }

  private String resolveLore(InteractionContext request) {
    final CampaignContext ctx = request.getCampaignContext();
    String loreContent;

    if( ctx.getCurrentTurn() < pipelineProps.getFullLoreTurns() ){
      loreContent = loreProvider.get(request.getLoreLangCode()).getAllLore();
    } else{
      ChatEntry lastTurn = ctx.getRecentHistory() != null && !ctx.getRecentHistory().isEmpty()
          ? ctx.getRecentHistory().getLast() : null;
      String questActive = ctx.getQuestLog() != null
          ? ctx.getQuestLog().questActive() : null;

      loreContent = loreRetrievalService.retrieve(request.getLoreLangCode(), request.getUserMessage(), ctx.getScene(), questActive, lastTurn);
      log.debug("Using RAG retrieval for turn={} ({} chars)", ctx.getCurrentTurn(), loreContent.length());
    }

    return "# CONOSCENZA DEL MONDO\n\n" + loreContent;
  }

  private LoreValidationResult validateParallel(String loreLangCode, TurnAgentsFactory agents,
                                                String draft, Collection<String> themes, Inventory inventory,
                                                Collection<ChatEntry> recentHistory) {

    List<CompletableFuture<ReviewerResponse>> futures = new ArrayList<>(themes.size());

    // Lore reviewers (dal Router)
    futures.addAll(themes.stream()
        .map(topic -> CompletableFuture.supplyAsync(
            TokenTrackingContext.wrap(() -> loreReviewerFactory.build(loreLangCode, topic).review(draft)), parallelExecutor))
        .toList());

    // Inventory reviewer — SEMPRE attivo, indipendente dal Router
    LoreReviewer inventoryReviewer = agents.buildInventoryReviewer(inventory, recentHistory);
    futures.add(CompletableFuture.supplyAsync(TokenTrackingContext.wrap(() -> inventoryReviewer.review(draft)), parallelExecutor));

    CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();

    Collection<ReviewerResponse> results = futures.stream()
        .map(CompletableFuture::join)
        .toList();

    Collection<ReviewerResponse> failures = results.stream()
        .filter(r -> !r.isPass())
        .toList();

    String aggregatedFeedback = failures.isEmpty() ? null : buildFeedback(failures);

    return new LoreValidationResult(failures.isEmpty(), failures, aggregatedFeedback);
  }

  private String buildFeedback(Collection<ReviewerResponse> failures) {
    String feedbackList = failures.stream()
        .map(f -> "- [" + f.category() + "]: " + f.feedbackReason())
        .collect(Collectors.joining("\n"));

    return loreReviewerFactory.getFeedbackTemplate().replace("{{feedbackList}}", feedbackList);
  }

  private void sendProgress(SseEmitter emitter, SseProgressCode code) throws IOException {
    sendEvent(emitter, SseEventType.PROGRESS, new SseProgressEvent(LocalDateTime.now(), code.getValue()));
  }

  private void sendEvent(SseEmitter emitter, SseEventType type, Object data) throws IOException {
    emitter.send(SseEmitter.event().name(type.getValue()).data(data, MediaType.APPLICATION_JSON));
  }

  private void handleError(SseEmitter emitter, String username, Exception ex) {
    if( isClientDisconnected(ex) ){
      log.warn("Client disconnected during pipeline execution for user: {}", username);
      return;
    }
    log.error("Error during pipeline execution for user: {}", username, ex);
    try{
      sendEvent(emitter, SseEventType.ERROR, new SseErrorEvent(LocalDateTime.now(), "GC_500_01", ex.getMessage()));
    }catch(IOException ignored){
      // client already disconnected
    }
    emitter.completeWithError(ex);
  }

  private static boolean isClientDisconnected(Throwable ex) {
    while( ex != null ){
      if( ex instanceof org.springframework.web.context.request.async.AsyncRequestNotUsableException ) return true;
      ex = ex.getCause();
    }
    return false;
  }

  private record GenerationResult( TurnAgentsFactory agents, String draft, boolean validated ) {
  }

}
