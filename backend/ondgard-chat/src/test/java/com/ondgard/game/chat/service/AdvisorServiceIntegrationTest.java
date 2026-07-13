package com.ondgard.game.chat.service;

import com.ondgard.game.chat.TestcontainersConfiguration;
import com.ondgard.game.chat.config.PipelineProperties;
import com.ondgard.game.chat.model.CampaignContext;
import com.ondgard.game.chat.model.InteractionContext;
import com.ondgard.game.chat.model.PlayerCharacter;
import com.ondgard.game.chat.repository.AdvisorLogRepository;
import com.ondgard.game.chat.repository.PlayerCharacterRepository;
import com.ondgard.game.chat.repository.projection.AdvisorLogProjection;
import com.ondgard.game.chat.service.agent.advisor.AdvisorAgent;
import com.ondgard.game.chat.service.agent.advisor.AdvisorTools;
import com.ondgard.game.chat.service.rag.LoreRetrievalService;
import com.ondgard.game.header.GameUserHeader;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
class AdvisorServiceIntegrationTest extends TestcontainersConfiguration {

  @Autowired private AdvisorLogService advisorLogService;
  @Autowired private AdvisorLogRepository advisorLogRepository;
  @Autowired private PipelineProperties pipelineProps;
  @Autowired private ExecutorService parallelExecutor;
  @Autowired private CampaignService campaignService;
  @Autowired private PlayerCharacterRepository playerCharacterRepository;
  @Autowired private PlatformTransactionManager txManager;
  @Autowired private EntityManager em;
  @Autowired private RedisTemplate<String, CampaignContext> campaignCtxtRedis;
  @Autowired private StringRedisTemplate stringRedisTemplate;

  @MockitoBean private AdvisorAgent advisorAgent;
  @MockitoBean private LoreRetrievalService loreRetrievalService;

  private static final UUID POSTMAN_USER_HASH = UUID.fromString("997a7552-5c1b-42c6-a0c0-094c50a0ad53");
  private static final String CTX_PREFIX = "ondgard:ctx:";
  private static final String TIMER_PREFIX = "ondgard:session-timer:";
  private static final String ACTIVE_SESSIONS_KEY = "ondgard:active-sessions";

  private final List<String> createdHashes = new ArrayList<>();

  @AfterEach
  void cleanup() {
    for( String hash : createdHashes ){
      cleanupRedisKeys(hash);
      cleanupDb(hash);
    }
    createdHashes.clear();
  }

  // ************************* tests *************************

  @Test
  void processAsk_persistsConversationToDatabase() {
    String hash = createCharacterHash();
    CampaignContext ctx = campaignService.load(POSTMAN_USER_HASH, hash);

    when(advisorAgent.answer(anyString(), anyString(), any(), anyString(), any(AdvisorTools.class)))
        .thenReturn("I nani sono un popolo fiero e laborioso.");

    createAdvisorService().processAsk(buildUserHeader(), buildInteractionContext(ctx, "Chi sono i nani?"));

    awaitPersistence(ctx.getCampaignId(), 1);

    List<AdvisorLogProjection> logs = advisorLogRepository.findRecentByCampaign(
        ctx.getCampaignId(), PageRequest.of(0, 10));
    assertThat(logs).hasSize(1);

    AdvisorLogProjection entry = logs.getFirst();
    assertThat(entry.userMessage()).isEqualTo("Chi sono i nani?");
    assertThat(entry.advisorResponse()).isEqualTo("I nani sono un popolo fiero e laborioso.");
    assertThat(entry.turnNumber()).isEqualTo(ctx.getCurrentTurn());
    assertThat(entry.created()).isNotNull();
  }

  @Test
  void processAsk_multipleInteractions_persistsAllExchanges() {
    String hash = createCharacterHash();
    CampaignContext ctx = campaignService.load(POSTMAN_USER_HASH, hash);

    when(advisorAgent.answer(anyString(), anyString(), any(), eq("Domanda 1"), any(AdvisorTools.class)))
        .thenReturn("Risposta 1");
    when(advisorAgent.answer(anyString(), anyString(), any(), eq("Domanda 2"), any(AdvisorTools.class)))
        .thenReturn("Risposta 2");
    when(advisorAgent.answer(anyString(), anyString(), any(), eq("Domanda 3"), any(AdvisorTools.class)))
        .thenReturn("Risposta 3");

    // Serialize interactions: wait for each to persist before sending the next,
    // so that chronological order is deterministic.
    int expected = 0;
    for( String question : List.of("Domanda 1", "Domanda 2", "Domanda 3") ){
      createAdvisorService().processAsk(buildUserHeader(), buildInteractionContext(ctx, question));
      awaitPersistence(ctx.getCampaignId(), ++expected);
    }

    // findRecent returns chronological order (ASC)
    List<AdvisorLogProjection> logs = advisorLogService.findRecent(ctx.getCampaignId());
    assertThat(logs).hasSize(3);
    assertThat(logs.get(0).userMessage()).isEqualTo("Domanda 1");
    assertThat(logs.get(0).advisorResponse()).isEqualTo("Risposta 1");
    assertThat(logs.get(1).userMessage()).isEqualTo("Domanda 2");
    assertThat(logs.get(1).advisorResponse()).isEqualTo("Risposta 2");
    assertThat(logs.get(2).userMessage()).isEqualTo("Domanda 3");
    assertThat(logs.get(2).advisorResponse()).isEqualTo("Risposta 3");
  }

  @Test
  @SuppressWarnings( "unchecked" )
  void processAsk_subsequentCall_advisorReceivesPreviousHistory() {
    String hash = createCharacterHash();
    CampaignContext ctx = campaignService.load(POSTMAN_USER_HASH, hash);

    when(advisorAgent.answer(anyString(), anyString(), any(), eq("Prima domanda"), any(AdvisorTools.class)))
        .thenReturn("Prima risposta");
    when(advisorAgent.answer(anyString(), anyString(), any(), eq("Seconda domanda"), any(AdvisorTools.class)))
        .thenReturn("Seconda risposta");

    // First interaction
    createAdvisorService().processAsk(buildUserHeader(), buildInteractionContext(ctx, "Prima domanda"));
    awaitPersistence(ctx.getCampaignId(), 1);

    // Second interaction — advisor should receive history with first exchange
    createAdvisorService().processAsk(buildUserHeader(), buildInteractionContext(ctx, "Seconda domanda"));
    awaitPersistence(ctx.getCampaignId(), 2);

    ArgumentCaptor<Collection<AdvisorLogProjection>> historyCaptor =
        ArgumentCaptor.forClass(Collection.class);
    verify(advisorAgent, times(2)).answer(anyString(), anyString(), historyCaptor.capture(), anyString(), any(AdvisorTools.class));

    List<Collection<AdvisorLogProjection>> allHistories = historyCaptor.getAllValues();

    // First call: empty history (no previous exchanges)
    assertThat(allHistories.get(0)).isEmpty();

    // Second call: one previous exchange
    Collection<AdvisorLogProjection> secondHistory = allHistories.get(1);
    assertThat(secondHistory).hasSize(1);
    AdvisorLogProjection prev = secondHistory.iterator().next();
    assertThat(prev.userMessage()).isEqualTo("Prima domanda");
    assertThat(prev.advisorResponse()).isEqualTo("Prima risposta");
  }

  @Test
  void processAsk_advisorRegistersFact_factsPropagatedToRedisContext() {
    String hash = createCharacterHash();
    CampaignContext ctx = campaignService.load(POSTMAN_USER_HASH, hash);

    when(advisorAgent.answer(anyString(), anyString(), any(), anyString(), any(AdvisorTools.class)))
        .thenAnswer(invocation -> {
          AdvisorTools tools = invocation.getArgument(4);
          tools.registerAdvisorFact("Il taverniere si chiama Gundren");
          return "Il taverniere si chiama Gundren, un nano robusto.";
        });

    createAdvisorService().processAsk(buildUserHeader(),
        buildInteractionContextWithCharacter(ctx, hash, "Come si chiama il taverniere?"));

    awaitPersistence(ctx.getCampaignId(), 1);

    CampaignContext cached = campaignCtxtRedis.opsForValue().get(CTX_PREFIX + hash);
    assertThat(cached).isNotNull();
    assertThat(cached.getAdvisorFacts()).containsExactly("Il taverniere si chiama Gundren");
  }

  @Test
  void processAsk_advisorNoFacts_contextUnchanged() {
    String hash = createCharacterHash();
    CampaignContext ctx = campaignService.load(POSTMAN_USER_HASH, hash);

    when(advisorAgent.answer(anyString(), anyString(), any(), anyString(), any(AdvisorTools.class)))
        .thenReturn("I nani vivono nelle montagne.");

    createAdvisorService().processAsk(buildUserHeader(),
        buildInteractionContextWithCharacter(ctx, hash, "Dove vivono i nani?"));

    awaitPersistence(ctx.getCampaignId(), 1);

    CampaignContext cached = campaignCtxtRedis.opsForValue().get(CTX_PREFIX + hash);
    assertThat(cached).isNotNull();
    assertThat(cached.getAdvisorFacts()).isEmpty();
  }

  @Test
  void processAsk_advisorThrows_doesNotPersist() {
    String hash = createCharacterHash();
    CampaignContext ctx = campaignService.load(POSTMAN_USER_HASH, hash);

    when(advisorAgent.answer(anyString(), anyString(), any(), anyString(), any(AdvisorTools.class)))
        .thenThrow(new RuntimeException("LLM unavailable"));

    createAdvisorService().processAsk(buildUserHeader(), buildInteractionContext(ctx, "Domanda fallita"));

    // Allow time for async pipeline to complete (with error)
    waitBriefly();

    List<AdvisorLogProjection> logs = advisorLogRepository.findRecentByCampaign(
        ctx.getCampaignId(), PageRequest.of(0, 10));
    assertThat(logs).isEmpty();
  }

  // ************************* helpers *************************

  /**
   * Constructs AdvisorService manually to bypass @RequestScope.
   */
  private AdvisorService createAdvisorService() {
    return new AdvisorService(parallelExecutor, advisorAgent, advisorLogService, campaignService, loreRetrievalService, pipelineProps);
  }

  private GameUserHeader buildUserHeader() {
    return GameUserHeader.builder()
        .userId(POSTMAN_USER_HASH.toString())
        .username("testuser")
        .language("it")
        .build();
  }

  private InteractionContext buildInteractionContext(CampaignContext ctx, String userMessage) {
    return InteractionContext.builder()
        .userMessage(userMessage)
        .campaignContext(ctx)
        .outputLang("Italian")
        .loreLangCode("it")
        .build();
  }

  private InteractionContext buildInteractionContextWithCharacter(CampaignContext ctx, String characterHash, String userMessage) {
    return InteractionContext.builder()
        .userMessage(userMessage)
        .campaignContext(ctx)
        .character(PlayerCharacter.builder().characterHash(characterHash).build())
        .outputLang("Italian")
        .loreLangCode("it")
        .build();
  }

  private void runInTransaction(Runnable action) {
    new TransactionTemplate(txManager).executeWithoutResult(status -> action.run());
  }

  private String createCharacterHash() {
    String hash = "test-char-" + UUID.randomUUID().toString().substring(0, 20);
    runInTransaction(() ->
        playerCharacterRepository.insertCharacter(POSTMAN_USER_HASH, "ELF", hash, "TestHero", "A test character")
    );
    createdHashes.add(hash);
    return hash;
  }

  private void cleanupRedisKeys(String characterHash) {
    campaignCtxtRedis.delete(CTX_PREFIX + characterHash);
    stringRedisTemplate.delete(TIMER_PREFIX + characterHash);
    stringRedisTemplate.opsForZSet().remove(ACTIVE_SESSIONS_KEY, characterHash);
  }

  private void cleanupDb(String characterHash) {
    runInTransaction(() -> {
      em.createNativeQuery(
              "DELETE FROM advisor_log WHERE campaign_id IN " +
                  "(SELECT c.character_id FROM campaign c JOIN player_character pc ON pc.id = c.character_id WHERE pc.character_hash = :hash)")
          .setParameter("hash", characterHash)
          .executeUpdate();
      em.createNativeQuery(
              "DELETE FROM campaign WHERE character_id IN " +
                  "(SELECT id FROM player_character WHERE character_hash = :hash)")
          .setParameter("hash", characterHash)
          .executeUpdate();
    });
  }

  /**
   * Polls the database until at least {@code expectedCount} advisor log rows exist.
   * Handles the two-level async: CompletableFuture pipeline + @Async persist.
   */
  private void awaitPersistence(Long campaignId, int expectedCount) {
    for( int i = 0; i < 50; i++ ){
      List<AdvisorLogProjection> logs = advisorLogRepository.findRecentByCampaign(
          campaignId, PageRequest.of(0, 100));
      if( logs.size() >= expectedCount ) return;
      try{
        Thread.sleep(100);
      }catch(InterruptedException e){
        Thread.currentThread().interrupt();
        break;
      }
    }
  }

  /**
   * Waits briefly for async tasks to settle (used for negative assertions).
   */
  private void waitBriefly() {
    try{
      Thread.sleep(2000);
    }catch(InterruptedException e){
      Thread.currentThread().interrupt();
    }
  }
}
