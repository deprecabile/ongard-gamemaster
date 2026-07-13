package com.ondgard.game.chat.service;

import com.google.gson.Gson;
import com.ondgard.game.GameGsonFactory;
import com.ondgard.game.chat.TestcontainersConfiguration;
import com.ondgard.game.chat.entity.CampaignEntity;
import com.ondgard.game.chat.model.CampaignContext;
import com.ondgard.game.chat.model.ChatEntry;
import com.ondgard.game.chat.model.SummaryPendingResult;
import com.ondgard.game.chat.repository.CampaignRepository;
import com.ondgard.game.chat.repository.PlayerCharacterRepository;
import com.ondgard.game.chat.service.agent.SummaryAgent;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest
class SummaryServiceIntegrationTest extends TestcontainersConfiguration {

  @Autowired private SummaryService summaryService;
  @Autowired private CampaignService campaignService;
  @Autowired private CampaignRepository campaignRepository;
  @Autowired private PlayerCharacterRepository playerCharacterRepository;
  @Autowired private RedisTemplate<String, CampaignContext> campaignCtxtRedis;
  @Autowired private StringRedisTemplate stringRedisTemplate;
  @Autowired private PlatformTransactionManager txManager;
  @Autowired private EntityManager em;

  @MockitoBean private SummaryAgent summaryAgent;

  private static final UUID POSTMAN_USER_HASH = UUID.fromString("997a7552-5c1b-42c6-a0c0-094c50a0ad53");
  private static final String CTX_PREFIX = "ondgard:ctx:";
  private static final String TIMER_PREFIX = "ondgard:session-timer:";
  private static final String ACTIVE_SESSIONS_KEY = "ondgard:active-sessions";
  private static final String PENDING_PREFIX = "ondgard:summary-pending:";

  private final Gson gson = GameGsonFactory.build();
  private final List<String> createdHashes = new ArrayList<>();

  @AfterEach
  void cleanup() {
    for( String hash : createdHashes ){
      campaignCtxtRedis.delete(CTX_PREFIX + hash);
      stringRedisTemplate.delete(TIMER_PREFIX + hash);
      stringRedisTemplate.delete(PENDING_PREFIX + hash);
      stringRedisTemplate.opsForZSet().remove(ACTIVE_SESSIONS_KEY, hash);
      cleanupDb(hash);
    }
    createdHashes.clear();
  }

  // ************************* helpers *************************

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

  private void cleanupDb(String characterHash) {
    runInTransaction(() -> {
      em.createNativeQuery(
              "DELETE FROM campaign WHERE character_id IN " +
                  "(SELECT id FROM player_character WHERE character_hash = :hash)")
          .setParameter("hash", characterHash)
          .executeUpdate();
    });
  }

  // ************************* integration tests *************************

  @Test
  void generate_sessionActive_writesPendingToRedis() {
    when(summaryAgent.generate(anyString(), any(), anyString())).thenReturn("Compressed narrative summary.");

    String hash = createCharacterHash();
    CampaignContext ctx = campaignService.load(POSTMAN_USER_HASH, hash);

    summaryService.generate("italiano", List.of(new ChatEntry(1, "explore", "found cave")),
        "Old summary.", 2, ctx.getCampaignId(), hash);

    // Pending key written to Redis
    String json = stringRedisTemplate.opsForValue().get(PENDING_PREFIX + hash);
    assertThat(json).isNotNull();

    SummaryPendingResult pending = gson.fromJson(json, SummaryPendingResult.class);
    assertThat(pending.narrativeSummary()).isEqualTo("Compressed narrative summary.");
    assertThat(pending.summaryVersion()).isEqualTo(2);

    // TTL set (should be close to 1 hour)
    Long ttl = stringRedisTemplate.getExpire(PENDING_PREFIX + hash);
    assertThat(ttl).isGreaterThan(3500);

    // DB not touched
    CampaignEntity entity = campaignRepository.findById(ctx.getCampaignId()).orElseThrow();
    assertThat(entity.getSummaryVersion()).isEqualTo(0);
  }

  @Test
  void generate_sessionExpired_writesDirectlyToDb() {
    when(summaryAgent.generate(anyString(), any(), anyString())).thenReturn("DB fallback summary.");

    String hash = createCharacterHash();
    CampaignContext ctx = campaignService.load(POSTMAN_USER_HASH, hash);
    Long campaignId = ctx.getCampaignId();

    // Simulate session ended — remove ctx key from Redis
    campaignCtxtRedis.delete(CTX_PREFIX + hash);

    summaryService.generate("italiano", List.of(new ChatEntry(1, "fight", "victory")),
        null, 1, campaignId, hash);

    // No pending key
    assertThat(stringRedisTemplate.hasKey(PENDING_PREFIX + hash)).isFalse();

    // Written directly to DB
    CampaignEntity entity = campaignRepository.findById(campaignId).orElseThrow();
    assertThat(entity.getNarrativeSummary()).isEqualTo("DB fallback summary.");
    assertThat(entity.getSummaryVersion()).isEqualTo(1);
  }

  @Test
  void generate_pendingPickedUpByCampaignServiceLoad() {
    when(summaryAgent.generate(anyString(), any(), anyString())).thenReturn("Integrated summary.");

    String hash = createCharacterHash();
    CampaignContext ctx = campaignService.load(POSTMAN_USER_HASH, hash);

    summaryService.generate("italiano", List.of(new ChatEntry(1, "action", "response")),
        "old", 1, ctx.getCampaignId(), hash);

    // Reload — should apply the pending summary
    CampaignContext reloaded = campaignService.load(POSTMAN_USER_HASH, hash);

    assertThat(reloaded.getNarrativeSummary()).isEqualTo("Integrated summary.");
    assertThat(reloaded.getSummaryVersion()).isEqualTo(1);

    // Pending key consumed
    assertThat(stringRedisTemplate.hasKey(PENDING_PREFIX + hash)).isFalse();
  }

  @Test
  void generate_agentThrows_noPendingAndNoDbChange() {
    when(summaryAgent.generate(anyString(), any(), anyString())).thenThrow(new RuntimeException("LLM timeout"));

    String hash = createCharacterHash();
    CampaignContext ctx = campaignService.load(POSTMAN_USER_HASH, hash);

    summaryService.generate("italiano", List.of(new ChatEntry(1, "action", "response")),
        "old", 1, ctx.getCampaignId(), hash);

    assertThat(stringRedisTemplate.hasKey(PENDING_PREFIX + hash)).isFalse();

    CampaignEntity entity = campaignRepository.findById(ctx.getCampaignId()).orElseThrow();
    assertThat(entity.getSummaryVersion()).isEqualTo(0);
    assertThat(entity.getNarrativeSummary()).isNull();
  }
}
