package com.ondgard.game.chat.service.campaign.setup;

import com.ondgard.game.chat.client.AccountClient;
import com.ondgard.game.chat.config.PipelineProperties;
import com.ondgard.game.chat.contract.campaign.setup.SetupGenerateRequest;
import com.ondgard.game.chat.contract.sse.campaign.setup.*;
import com.ondgard.game.chat.error.GameErrorCode;
import com.ondgard.game.chat.model.ChatEntry;
import com.ondgard.game.chat.model.GameRace;
import com.ondgard.game.chat.model.agent.NameRaceSetupResponse;
import com.ondgard.game.chat.model.setup.CampaignArchetype;
import com.ondgard.game.chat.model.setup.SetupGenerateMode;
import com.ondgard.game.chat.service.RaceService;
import com.ondgard.game.chat.service.SseHeartbeat;
import com.ondgard.game.chat.service.agent.setup.CampaignSetupAgentsFactory;
import com.ondgard.game.chat.service.agent.setup.CampaignSetupGeneratorAgent;
import com.ondgard.game.chat.service.agent.setup.CampaignSetupSummaryAgent;
import com.ondgard.game.chat.service.agent.setup.NameRaceSetupAgent;
import com.ondgard.game.chat.service.rag.RagReadinessGate;
import com.ondgard.game.contract.account.CheckLimitResponse;
import com.ondgard.game.exception.BadRequestException;
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
public class CampaignSetupGenerateService {

  private static final String DEFAULT_RACE_CODE = "UMN";

  private final ArchetypeService archetypeService;
  private final CampaignSetupSessionService sessionService;
  private final CampaignSetupAgentsFactory agentsFactory;
  private final PipelineProperties pipelineProps;
  @Qualifier( "parallelExecutor" ) private final ExecutorService parallelExecutor;
  private final RagReadinessGate ragReadinessGate;
  private final AccountClient accountClient;
  private final RaceService raceService;

  public SseEmitter generate(GameUserHeader userHeader, SetupGenerateRequest request) {
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

    CampaignArchetype archetype = archetypeService.getByCode(userHeader.getLanguage(), request.archetypeCode());
    if( archetype == null ){
      throw new BadRequestException("GC_400_01", "Invalid archetype code: " + request.archetypeCode());
    }

    if( request.mode() == SetupGenerateMode.SCENE && (request.characterPrompt() == null || request.characterPrompt().isBlank()) ){
      throw new BadRequestException("GC_400_02", "characterPrompt is required for SCENE mode");
    }

    var emitter = new SseEmitter(pipelineProps.getSseTimeout().toMillis());

    CompletableFuture.runAsync(() -> runPipeline(emitter, userHeader, request, archetype), parallelExecutor);

    emitter.onTimeout(() -> log.warn("SSE timeout for setup generate: userId={}", userHeader.getUserId()));
    emitter.onError(ex -> log.error("SSE error for setup generate: userId={}", userHeader.getUserId(), ex));

    return emitter;
  }

  // ========================= private =========================

  private void runPipeline(SseEmitter emitter, GameUserHeader userHeader,
                           SetupGenerateRequest request, CampaignArchetype archetype) {
    try{
      sendEvent(emitter, SseSetupGenerateEventType.STARTED, new SseSetupGenerateStartEvent(true));

      String summary = summarizeHistory(emitter, userHeader.getUserId(), userHeader.getLanguage());
      CampaignSetupGeneratorAgent generatorAgent = agentsFactory.buildGeneratorAgent(userHeader.getLanguage());

      String raceCode = null;
      String characterName = null;

      // Pick race + name (only FULL mode — picker decides; CHARACTER/SCENE use request values)
      if( request.mode() == SetupGenerateMode.FULL ){
        sendProgress(emitter, SseSetupGenerateProgressCode.SETUP_PICKING_NAME);
        try{
          NameRaceSetupAgent nameRaceAgent = agentsFactory.buildNameRaceSetupAgent(userHeader.getLanguage());
          try( var hb = new SseHeartbeat(emitter, 10,
              SseSetupGenerateEventType.PROGRESS.getValue(),
              SseSetupGenerateProgressCode.SETUP_PICKING_NAME) ){
            NameRaceSetupResponse picked = nameRaceAgent.pick(archetype.description(), summary);
            raceCode = resolveRaceCode(picked.raceCode());
            characterName = picked.characterName();
          }
        }catch(Exception ex){
          log.warn("Name/race picker failed, using defaults: {}", ex.getMessage());
          raceCode = DEFAULT_RACE_CODE;
        }

        sendEvent(emitter, SseSetupGenerateEventType.NAME_PICKED,
            new SseSetupGenerateNamePickedEvent(raceCode, characterName));
      }

      // Generate character (if FULL or CHARACTER)
      String characterPrompt = null;
      if( request.mode() == SetupGenerateMode.FULL || request.mode() == SetupGenerateMode.CHARACTER ){
        if( request.mode() == SetupGenerateMode.CHARACTER ){
          raceCode = resolveRaceCode(request.raceCode());
          characterName = request.characterName();
        }

        String raceName = resolveRaceName(raceCode);

        sendProgress(emitter, SseSetupGenerateProgressCode.SETUP_GENERATING_CHARACTER);
        try( var hb = new SseHeartbeat(emitter, 10,
            SseSetupGenerateEventType.PROGRESS.getValue(),
            SseSetupGenerateProgressCode.SETUP_GENERATING_CHARACTER) ){
          characterPrompt = generatorAgent.generate(SetupGenerateMode.CHARACTER, archetype, summary, null, raceName, characterName);
        }

        // In FULL mode, send character prompt immediately so user can read while scene generates
        if( request.mode() == SetupGenerateMode.FULL ){
          sendEvent(emitter, SseSetupGenerateEventType.CHARACTER_GENERATED,
              new SseSetupGenerateCharacterGeneratedEvent(characterPrompt));
        }
      }

      // Generate scene (if FULL or SCENE)
      String startingSituation = null;
      if( request.mode() == SetupGenerateMode.FULL || request.mode() == SetupGenerateMode.SCENE ){
        String cpForScene = request.mode() == SetupGenerateMode.FULL
            ? characterPrompt
            : request.characterPrompt();
        String rcForScene = request.mode() == SetupGenerateMode.FULL ? raceCode : resolveRaceCode(request.raceCode());
        String cnForScene = request.mode() == SetupGenerateMode.FULL ? characterName : request.characterName();
        String rnForScene = resolveRaceName(rcForScene);

        sendProgress(emitter, SseSetupGenerateProgressCode.SETUP_GENERATING_SCENE);
        try( var hb = new SseHeartbeat(emitter, 10,
            SseSetupGenerateEventType.PROGRESS.getValue(),
            SseSetupGenerateProgressCode.SETUP_GENERATING_SCENE) ){
          startingSituation = generatorAgent.generate(SetupGenerateMode.SCENE, archetype, summary, cpForScene, rnForScene, cnForScene);
        }
      }

      // In CHARACTER mode, don't send raceCode/characterName in completed (don't overwrite form)
      String completedRaceCode = request.mode() == SetupGenerateMode.FULL ? raceCode : null;
      String completedCharacterName = request.mode() == SetupGenerateMode.FULL ? characterName : null;

      sendEvent(emitter, SseSetupGenerateEventType.COMPLETED,
          new SseSetupGenerateCompletedEvent(LocalDateTime.now(), characterPrompt, startingSituation, completedRaceCode, completedCharacterName));

      updateRedisSession(userHeader, request, characterPrompt, startingSituation, raceCode, characterName);

      emitter.complete();
    }catch(Exception ex){
      handleError(emitter, userHeader.getUserId(), ex);
    }
  }

  private void updateRedisSession(GameUserHeader userHeader, SetupGenerateRequest request, String characterPrompt, String startingSituation, String raceCode, String characterName) {
    try{
      var session = sessionService.getOrCreate(userHeader.getUserId());
      session.setArchetypeCode(request.archetypeCode());
      if( characterPrompt != null ) session.setCharacterPrompt(characterPrompt);
      if( startingSituation != null ) session.setStartingSituation(startingSituation);
      if( request.mode() == SetupGenerateMode.FULL ){
        if( raceCode != null ) session.setRaceCode(raceCode);
        if( characterName != null ) session.setCharacterName(characterName);
      }
      sessionService.save(userHeader.getUserId(), session);
    }catch(Exception ex){
      log.warn("Failed to persist setup results for userId={}", userHeader.getUserId(), ex);
    }
  }

  private String resolveRaceCode(String raceCode) {
    if( raceCode == null || raceCode.isBlank() || raceService.getRace(raceCode) == null ){
      if( raceCode != null && !raceCode.isBlank() ){
        log.warn("Invalid raceCode '{}', falling back to {}", raceCode, DEFAULT_RACE_CODE);
      }
      return DEFAULT_RACE_CODE;
    }
    return raceCode;
  }

  private String resolveRaceName(String raceCode) {
    GameRace race = raceService.getRace(raceCode);
    return race != null ? race.getName() : "";
  }

  private String summarizeHistory(SseEmitter emitter, String userId, String lang) throws IOException {
    Collection<ChatEntry> history = sessionService.getHistory(userId);
    if( history.isEmpty() ){
      return null;
    }
    sendProgress(emitter, SseSetupGenerateProgressCode.SETUP_SUMMARIZING);
    CampaignSetupSummaryAgent summaryAgent = agentsFactory.buildSummaryAgent(lang);
    return summaryAgent.summarize(history);
  }

  private void sendProgress(SseEmitter emitter, SseSetupGenerateProgressCode code) throws IOException {
    sendEvent(emitter, SseSetupGenerateEventType.PROGRESS,
        new SseSetupGenerateProgressEvent(LocalDateTime.now(), code.getValue()));
  }

  private void sendEvent(SseEmitter emitter, SseSetupGenerateEventType type, Object data) throws IOException {
    emitter.send(SseEmitter.event().name(type.getValue()).data(data, MediaType.APPLICATION_JSON));
  }

  private void handleError(SseEmitter emitter, String userId, Exception ex) {
    if( isClientDisconnected(ex) ){
      log.warn("Client disconnected during setup generate for userId={}", userId);
      return;
    }
    log.error("Error during setup generate for userId={}", userId, ex);
    try{
      sendEvent(emitter, SseSetupGenerateEventType.ERROR,
          new SseSetupGenerateErrorEvent(LocalDateTime.now(), "GC_500_04", ex.getMessage()));
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
