package com.ondgard.game.chat.service;

import com.google.gson.Gson;
import com.ondgard.game.GameGsonFactory;
import com.ondgard.game.chat.config.SessionProperties;
import com.ondgard.game.chat.config.SummaryProperties;
import com.ondgard.game.chat.contract.CampaignListItem;
import com.ondgard.game.chat.contract.CampaignTurnResponse;
import com.ondgard.game.chat.entity.CampaignEntity;
import com.ondgard.game.chat.entity.CampaignQuestlogEntity;
import com.ondgard.game.chat.error.GameErrorCode;
import com.ondgard.game.chat.model.*;
import com.ondgard.game.chat.model.inventory.Inventory;
import com.ondgard.game.chat.repository.*;
import com.ondgard.game.chat.repository.projection.CampaignListProjection;
import com.ondgard.game.exception.AppException;
import com.ondgard.game.exception.ForbiddenException;
import com.ondgard.game.exception.NoResultException;
import com.ondgard.game.header.GameUserHeader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class CampaignService {

  private static final String CTX_PREFIX = "ondgard:ctx:";
  private static final String TIMER_PREFIX = "ondgard:session-timer:";
  private static final String ACTIVE_SESSIONS_KEY = "ondgard:active-sessions";

  private final CampaignRepository campaignRepository;
  private final AdventureLogRepository adventureLogRepository;
  private final CampaignQuestlogRepository campaignQuestlogRepository;
  private final CampaignNotesRepository campaignNotesRepository;
  private final PlayerInventoryRepository playerInventoryRepository;
  private final PlayerCharacterRepository playerCharacterRepository;
  private final CharacterService characterService;
  private final RedisTemplate<String, CampaignContext> campaignCtxtRedis;
  private final StringRedisTemplate stringRedisTemplate;
  private final SessionProperties sessionProperties;
  private final SummaryProperties summaryProps;

  @Transactional
  public CampaignContext load(UUID userHash, String characterHash) {
    // 1. Redis hit
    CampaignContext cached = campaignCtxtRedis.opsForValue().get(CTX_PREFIX + characterHash);
    if( cached != null ){
      log.debug("Campaign context cache hit for characterHash={}", characterHash);
      if( applyPendingSummary(cached, characterHash) ){
        campaignCtxtRedis.opsForValue().set(CTX_PREFIX + characterHash, cached);
      }
      touchSession(characterHash);
      return cached;
    }

    // 2. Redis miss → load from DB
    Optional<CampaignEntity> campaignOpt = campaignRepository.findByCharacterHashAndUserHash(userHash, characterHash);
    if( campaignOpt.isPresent() ){
      log.debug("Campaign context cache miss, loading from DB for characterHash={}", characterHash);
      CampaignEntity entity = campaignOpt.get();
      int actualTurn = Math.max(
          entity.getTurnCount(),
          adventureLogRepository.findMaxTurnNumber(entity.getCharacterId()));
      CampaignContext ctx = mapFromEntity(entity, actualTurn);

      ctx.setRecentHistory(loadRecentHistory(entity.getCharacterId()));
      ctx.setQuestLog(loadQuestLog(entity.getCharacterId()));
      ctx.setInventory(loadInventory(entity.getCharacterId()));
      ctx.setRawSummaryBuffer(rebuildRawBuffer(entity, actualTurn));
      applyPendingSummary(ctx, characterHash);

      campaignCtxtRedis.opsForValue().set(CTX_PREFIX + characterHash, ctx);
      touchSession(characterHash);
      return ctx;
    }

    // 3. First turn → create campaign
    log.info("No campaign found, creating new campaign for characterHash={}", characterHash);
    return initNewCampaign(userHash, characterHash);
  }

  public void saveContextOnly(CampaignContext ctx, String characterHash) {
    ctx.setLastUpdate(LocalDateTime.now());
    campaignCtxtRedis.opsForValue().set(CTX_PREFIX + characterHash, ctx);
  }

  public void save(CampaignContext ctx, String characterHash) {
    ctx.setLastUpdate(LocalDateTime.now());
    ctx.setTurnsSinceLastFlush(ctx.getTurnsSinceLastFlush() + 1);

    campaignCtxtRedis.opsForValue().set(CTX_PREFIX + characterHash, ctx);
    touchSession(characterHash);

    if( ctx.getTurnsSinceLastFlush() >= sessionProperties.getFlushEveryNTurns() ){
      log.info("Flush threshold reached ({} turns) for characterHash={}", ctx.getTurnsSinceLastFlush(), characterHash);
      flushToDb(ctx);
      ctx.setTurnsSinceLastFlush(0);
      campaignCtxtRedis.opsForValue().set(CTX_PREFIX + characterHash, ctx);
    }
  }

  public void flush(String characterHash) {
    final CampaignContext ctx = campaignCtxtRedis.opsForValue().get(CTX_PREFIX + characterHash);
    if( ctx != null ){
      applyPendingSummary(ctx, characterHash);
      flushToDb(ctx);
    }
    campaignCtxtRedis.delete(CTX_PREFIX + characterHash);
    stringRedisTemplate.delete(TIMER_PREFIX + characterHash);
    stringRedisTemplate.delete(SummaryService.PENDING_PREFIX + characterHash);
    stringRedisTemplate.opsForZSet().remove(ACTIVE_SESSIONS_KEY, characterHash);
    log.info("Session flushed and cleaned for characterHash={}", characterHash);
  }

  public void claimAndFlush(String characterHash) {
    Long removed = stringRedisTemplate.opsForZSet().remove(ACTIVE_SESSIONS_KEY, characterHash);
    if( removed != null && removed == 1 ){
      log.info("Claimed expired session for characterHash={}, flushing", characterHash);
      CampaignContext ctx = campaignCtxtRedis.opsForValue().get(CTX_PREFIX + characterHash);
      if( ctx != null ){
        applyPendingSummary(ctx, characterHash);
        flushToDb(ctx);
      }
      campaignCtxtRedis.delete(CTX_PREFIX + characterHash);
      stringRedisTemplate.delete(SummaryService.PENDING_PREFIX + characterHash);
    } else{
      log.debug("Session for characterHash={} already claimed by another instance", characterHash);
    }
  }

  public CampaignTurnResponse getTurn(GameUserHeader userHeader, String characterHash) {
    try{
      characterService.getCharacter(userHeader, characterHash);
    }catch(NoResultException ex){
      throw new ForbiddenException("Access denied");
    }
    CampaignContext ctx = load(UUID.fromString(userHeader.getUserId()), characterHash);
    return CampaignTurnResponse.builder()
        .characterHash(characterHash)
        .currentTurn(ctx.getCurrentTurn())
        .inventory(ctx.getInventory())
        .questLog(ctx.getQuestLog())
        .scene(ctx.getScene())
        .lastUpdate(ctx.getLastUpdate())
        .build();
  }

  public boolean hasCampaigns(GameUserHeader userHeader) {
    UUID userHash = UUID.fromString(userHeader.getUserId());
    return campaignRepository.existsByUserHash(userHash);
  }

  public List<CampaignListItem> listCampaigns(GameUserHeader userHeader) {
    UUID userHash = UUID.fromString(userHeader.getUserId());
    List<CampaignListProjection> projections = campaignRepository.findAllByUserHash(userHash);
    return projections.stream()
        .map(p -> CampaignListItem.builder()
            .characterHash(p.characterHash())
            .characterName(p.characterName())
            .raceCode(p.raceCode())
            .currentTurn(p.turnCount())
            .currentLocation(p.currentLocation())
            .lastUpdate(p.updated())
            .narrativePreview(buildNarrativePreview(p))
            .build())
        .toList();
  }

  public List<ChatEntry> getHistory(GameUserHeader userHeader, String characterHash) {
    try{
      characterService.getCharacter(userHeader, characterHash);
    }catch(NoResultException ex){
      throw new ForbiddenException("Access denied");
    }

    UUID userHash = UUID.fromString(userHeader.getUserId());
    CampaignEntity entity = campaignRepository.findByCharacterHashAndUserHash(userHash, characterHash)
        .orElseThrow(NoResultException::new);

    var projections = adventureLogRepository.findRecentTurns(
        entity.getCharacterId(), 20);
    if( projections.isEmpty() ) return List.of();

    List<ChatEntry> history = new ArrayList<>(projections.stream()
        .map(p -> new ChatEntry(p.turnNumber(), p.userMessage(), p.gmResponse()))
        .toList());
    Collections.reverse(history);
    return history;
  }

  @Transactional
  public void deleteCampaign(GameUserHeader userHeader, String characterHash) {
    try{
      characterService.getCharacter(userHeader, characterHash);
    }catch(NoResultException ex){
      throw new ForbiddenException("Access denied");
    }

    campaignCtxtRedis.delete(CTX_PREFIX + characterHash);
    stringRedisTemplate.delete(TIMER_PREFIX + characterHash);
    stringRedisTemplate.delete(SummaryService.PENDING_PREFIX + characterHash);
    stringRedisTemplate.opsForZSet().remove(ACTIVE_SESSIONS_KEY, characterHash);

    UUID userHash = UUID.fromString(userHeader.getUserId());
    int deleted = playerCharacterRepository.deleteByCharacterHashAndUserHash(characterHash, userHash);
    if( deleted == 0 ) throw new NoResultException();
    log.info("Deleted campaign and character for characterHash={}", characterHash);
  }

  // ************************************ private ************************************

  private @NonNull CampaignContext initNewCampaign(UUID userHash, String characterHash) {
    try{
      campaignRepository.insertCampaign(userHash, characterHash);
    }catch(Exception ex){
      log.error("Failed to create campaign for characterHash={}: {}", characterHash, ex.getMessage(), ex);
      throw new AppException(GameErrorCode.CAMPAIGN_INIT_FAILED.getCode() + ": Failed to initialize campaign");
    }

    CampaignEntity newEntity = campaignRepository.findByCharacterHashAndUserHash(userHash, characterHash).orElseThrow(NoResultException::new);
    campaignNotesRepository.insertEmpty(newEntity.getCharacterId());

    final CampaignContext ctx = mapFromEntity(newEntity, 0);
    ctx.setInventory(Inventory.empty());
    ctx.setQuestLog(new CampaignQuestLog(0, "", ""));
    ctx.setRecentHistory(new ArrayList<>());

    campaignCtxtRedis.opsForValue().set(CTX_PREFIX + characterHash, ctx);
    touchSession(characterHash);
    return ctx;
  }

  @Transactional
  protected void flushToDb(CampaignContext ctx) {
    campaignRepository.findById(ctx.getCampaignId()).ifPresent(entity -> {
      entity.setNarrativeSummary(ctx.getNarrativeSummary());
      entity.setSummaryVersion(ctx.getSummaryVersion());
      entity.setTurnCount(ctx.getCurrentTurn());
      entity.setLastSummaryAtTurn(ctx.getCurrentTurn() - ctx.getTurnsSinceLastSummary());

      GameScene scene = ctx.getScene();
      if( scene != null ){
        entity.setCurrentLocation(scene.getCurrentLocation());
        entity.setGameDate(scene.getGameDate());
        entity.setGameTime(scene.getGameTime());
        entity.setMeteo(scene.getMeteo());
        entity.setTemperature(scene.getTemperature());
      }

      campaignRepository.save(entity);
      log.debug("Campaign flushed to DB for campaignId={}", ctx.getCampaignId());
    });
  }

  private List<ChatEntry> loadRecentHistory(Long campaignId) {
    var projections = adventureLogRepository.findRecentTurns(campaignId, summaryProps.getRecentHistorySize());
    if( projections.isEmpty() ){
      return new ArrayList<>();
    }
    List<ChatEntry> history = new ArrayList<>(projections.stream()
        .map(p -> new ChatEntry(p.turnNumber(), p.userMessage(), p.gmResponse()))
        .toList());
    Collections.reverse(history);
    return history;
  }

  private CampaignQuestLog loadQuestLog(Long campaignId) {
    return campaignQuestlogRepository.findLatestByCampaignId(campaignId)
        .map(this::mapQuestLog)
        .orElse(new CampaignQuestLog(0, "", ""));
  }

  private Inventory loadInventory(Long campaignId) {
    return playerInventoryRepository.findLatestByCampaignId(campaignId)
        .map(e -> e.getInventory())
        .orElse(Inventory.empty());
  }

  private CampaignQuestLog mapQuestLog(CampaignQuestlogEntity entity) {
    return new CampaignQuestLog(entity.getTurnNumber(), entity.getQuestActive(), entity.getQuestCompleted());
  }

  private CampaignContext mapFromEntity(CampaignEntity entity, int currentTurn) {
    return CampaignContext.builder()
        .campaignId(entity.getCharacterId())
        .narrativeSummary(entity.getNarrativeSummary())
        .summaryVersion(entity.getSummaryVersion())
        .currentTurn(currentTurn)
        .turnsSinceLastSummary(currentTurn - entity.getLastSummaryAtTurn())
        .turnsSinceLastFlush(0)
        .lastUpdate(entity.getUpdated())
        .scene(GameScene.builder()
            .currentLocation(entity.getCurrentLocation())
            .gameDate(entity.getGameDate())
            .gameTime(entity.getGameTime())
            .meteo(entity.getMeteo())
            .temperature(entity.getTemperature())
            .build())
        .build();
  }

  private boolean applyPendingSummary(CampaignContext ctx, String characterHash) {
    try{
      String json = stringRedisTemplate.opsForValue().get(SummaryService.PENDING_PREFIX + characterHash);
      if( json == null ){
        return false;
      }
      Gson gson = GameGsonFactory.build();
      SummaryPendingResult pending = gson.fromJson(json, SummaryPendingResult.class);
      stringRedisTemplate.delete(SummaryService.PENDING_PREFIX + characterHash);

      if( pending.summaryVersion() <= ctx.getSummaryVersion() ){
        log.debug("Stale pending summary for characterHash={} (pending={}, current={})",
            characterHash, pending.summaryVersion(), ctx.getSummaryVersion());
        return false;
      }

      ctx.setNarrativeSummary(pending.narrativeSummary());
      ctx.setSummaryVersion(pending.summaryVersion());
      log.info("Applied pending summary for characterHash={}, version={}", characterHash, pending.summaryVersion());
      return true;
    }catch(Exception ex){
      log.error("Failed to apply pending summary for characterHash={}: {}", characterHash, ex.getMessage(), ex);
      stringRedisTemplate.delete(SummaryService.PENDING_PREFIX + characterHash);
      return false;
    }
  }

  private List<ChatEntry> rebuildRawBuffer(CampaignEntity entity, int currentTurn) {
    int bufferStart = entity.getLastSummaryAtTurn() + 1;
    int bufferEnd = currentTurn - summaryProps.getRecentHistorySize();
    if( bufferStart <= bufferEnd && bufferEnd > 0 ){
      var turns = adventureLogRepository.findTurnRange(entity.getCharacterId(), bufferStart, bufferEnd);
      return new ArrayList<>(turns.stream()
          .map(t -> new ChatEntry(t.turnNumber(), t.userMessage(), t.gmResponse()))
          .toList());
    }
    return new ArrayList<>();
  }

  private void touchSession(String characterHash) {
    double score = System.currentTimeMillis();
    stringRedisTemplate.opsForZSet().add(ACTIVE_SESSIONS_KEY, characterHash, score);
    stringRedisTemplate.opsForValue().set(
        TIMER_PREFIX + characterHash, "",
        sessionProperties.getInactivityTimeout()
    );
  }

  private String buildNarrativePreview(CampaignListProjection p) {
    if( p.narrativeSummary() != null && !p.narrativeSummary().isBlank() ){
      return p.narrativeSummary();
    }
    String desc = p.description() != null ? p.description() : "";
    String firstGm = adventureLogRepository.findFirstGmResponse(p.campaignId()).orElse("");
    return (desc + "\n\n" + firstGm).strip();
  }

  public String getQuestActive(GameUserHeader userHeader, String characterHash) {
    try{
      characterService.getCharacter(userHeader, characterHash);
    }catch(NoResultException ex){
      throw new ForbiddenException("Access denied");
    }

    UUID userHash = UUID.fromString(userHeader.getUserId());
    CampaignEntity entity = campaignRepository.findByCharacterHashAndUserHash(userHash, characterHash)
        .orElseThrow(NoResultException::new);

    return campaignQuestlogRepository.findLatestByCampaignId(entity.getCharacterId())
        .map(CampaignQuestlogEntity::getQuestActive)
        .orElse(null);
  }
}
