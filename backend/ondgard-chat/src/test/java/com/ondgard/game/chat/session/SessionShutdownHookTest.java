package com.ondgard.game.chat.session;

import com.ondgard.game.chat.TestcontainersConfiguration;
import com.ondgard.game.chat.entity.CampaignEntity;
import com.ondgard.game.chat.model.CampaignContext;
import com.ondgard.game.chat.model.GameScene;
import com.ondgard.game.chat.repository.CampaignRepository;
import com.ondgard.game.chat.repository.PlayerCharacterRepository;
import com.ondgard.game.chat.service.CampaignService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource( properties = "server.shutdown=graceful" )
class SessionShutdownHookTest extends TestcontainersConfiguration {

  @Autowired private SessionShutdownHook sessionShutdownHook;
  @Autowired private CampaignService campaignService;
  @Autowired private CampaignRepository campaignRepository;
  @Autowired private PlayerCharacterRepository playerCharacterRepository;
  @Autowired private RedisTemplate<String, CampaignContext> campaignCtxtRedis;
  @Autowired private StringRedisTemplate stringRedisTemplate;
  @Autowired private PlatformTransactionManager txManager;
  @Autowired private EntityManager em;

  private static final UUID POSTMAN_USER_HASH = UUID.fromString("997a7552-5c1b-42c6-a0c0-094c50a0ad53");
  private static final String CTX_PREFIX = "ondgard:ctx:";
  private static final String TIMER_PREFIX = "ondgard:session-timer:";
  private static final String ACTIVE_SESSIONS_KEY = "ondgard:active-sessions";

  private final List<String> createdHashes = new ArrayList<>();

  @BeforeEach
  void setUp() {
    Set<String> stale = stringRedisTemplate.opsForZSet().range(ACTIVE_SESSIONS_KEY, 0, -1);
    if( stale != null ){
      for( String hash : stale ){
        campaignCtxtRedis.delete(CTX_PREFIX + hash);
        stringRedisTemplate.delete(TIMER_PREFIX + hash);
        stringRedisTemplate.opsForZSet().remove(ACTIVE_SESSIONS_KEY, hash);
      }
    }
  }

  @AfterEach
  void cleanup() {
    for( String hash : createdHashes ){
      campaignCtxtRedis.delete(CTX_PREFIX + hash);
      stringRedisTemplate.delete(TIMER_PREFIX + hash);
      stringRedisTemplate.opsForZSet().remove(ACTIVE_SESSIONS_KEY, hash);
      cleanupDb(hash);
    }
    createdHashes.clear();
  }

  private void runInTransaction(Runnable action) {
    new TransactionTemplate(txManager).executeWithoutResult(status -> action.run());
  }

  private String createCharacterAndSession(String location) {
    String hash = "test-char-" + UUID.randomUUID().toString().substring(0, 20);
    runInTransaction(() ->
        playerCharacterRepository.insertCharacter(POSTMAN_USER_HASH, "ELF", hash, "TestHero", "A test character")
    );
    createdHashes.add(hash);

    CampaignContext ctx = campaignService.load(POSTMAN_USER_HASH, hash);
    ctx.setScene(GameScene.builder().currentLocation(location).build());
    ctx.setCurrentTurn(3);
    ctx.setNarrativeSummary("A story in " + location);
    campaignService.saveContextOnly(ctx, hash);

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

  @Test
  void flushAllSessions_noActiveSessions_completesQuietly() {
    sessionShutdownHook.flushAllSessions();
  }

  @Test
  void flushAllSessions_persistsAllSessionsToDb() {
    String hashA = createCharacterAndSession("Dark Forest");
    String hashB = createCharacterAndSession("Mountain Peak");

    CampaignContext ctxA = campaignCtxtRedis.opsForValue().get(CTX_PREFIX + hashA);
    CampaignContext ctxB = campaignCtxtRedis.opsForValue().get(CTX_PREFIX + hashB);
    Long campaignIdA = ctxA.getCampaignId();
    Long campaignIdB = ctxB.getCampaignId();

    // Before shutdown: data is in Redis, not yet flushed to DB
    CampaignEntity beforeA = campaignRepository.findById(campaignIdA).orElseThrow();
    assertThat(beforeA.getCurrentLocation()).isNull();

    // Simulate graceful shutdown
    sessionShutdownHook.flushAllSessions();

    // After shutdown: data persisted to DB
    CampaignEntity afterA = campaignRepository.findById(campaignIdA).orElseThrow();
    assertThat(afterA.getCurrentLocation()).isEqualTo("Dark Forest");
    assertThat(afterA.getTurnCount()).isEqualTo(3);
    assertThat(afterA.getNarrativeSummary()).isEqualTo("A story in Dark Forest");

    CampaignEntity afterB = campaignRepository.findById(campaignIdB).orElseThrow();
    assertThat(afterB.getCurrentLocation()).isEqualTo("Mountain Peak");
    assertThat(afterB.getTurnCount()).isEqualTo(3);
    assertThat(afterB.getNarrativeSummary()).isEqualTo("A story in Mountain Peak");
  }

  @Test
  void flushAllSessions_cleansRedisKeys() {
    String hashA = createCharacterAndSession("Forest");
    String hashB = createCharacterAndSession("Cave");

    // Before: Redis has context and sorted set entries
    assertThat(campaignCtxtRedis.hasKey(CTX_PREFIX + hashA)).isTrue();
    assertThat(campaignCtxtRedis.hasKey(CTX_PREFIX + hashB)).isTrue();
    assertThat(stringRedisTemplate.opsForZSet().score(ACTIVE_SESSIONS_KEY, hashA)).isNotNull();
    assertThat(stringRedisTemplate.opsForZSet().score(ACTIVE_SESSIONS_KEY, hashB)).isNotNull();

    sessionShutdownHook.flushAllSessions();

    // After: all Redis keys cleaned
    assertThat(campaignCtxtRedis.hasKey(CTX_PREFIX + hashA)).isFalse();
    assertThat(campaignCtxtRedis.hasKey(CTX_PREFIX + hashB)).isFalse();
    assertThat(stringRedisTemplate.opsForZSet().score(ACTIVE_SESSIONS_KEY, hashA)).isNull();
    assertThat(stringRedisTemplate.opsForZSet().score(ACTIVE_SESSIONS_KEY, hashB)).isNull();
  }

  @Test
  void flushAllSessions_concurrentClaim_secondCallIsNoop() {
    String hash = createCharacterAndSession("River");

    CampaignContext ctx = campaignCtxtRedis.opsForValue().get(CTX_PREFIX + hash);
    Long campaignId = ctx.getCampaignId();

    // First shutdown claims and flushes
    sessionShutdownHook.flushAllSessions();

    CampaignEntity afterFirst = campaignRepository.findById(campaignId).orElseThrow();
    assertThat(afterFirst.getCurrentLocation()).isEqualTo("River");

    // Simulate second instance also shutting down — session already claimed
    // Manually re-add to sorted set to simulate stale snapshot read
    stringRedisTemplate.opsForZSet().add(ACTIVE_SESSIONS_KEY, hash, System.currentTimeMillis());

    // Modify DB to detect if second call overwrites (it shouldn't re-flush since context is gone)
    runInTransaction(() -> {
      CampaignEntity entity = campaignRepository.findById(campaignId).orElseThrow();
      entity.setCurrentLocation("MODIFIED_AFTER_FIRST_FLUSH");
      campaignRepository.save(entity);
    });

    // Second shutdown: claimAndFlush succeeds (ZREM returns 1) but ctx is null in Redis → no DB write
    sessionShutdownHook.flushAllSessions();

    CampaignEntity afterSecond = campaignRepository.findById(campaignId).orElseThrow();
    assertThat(afterSecond.getCurrentLocation()).isEqualTo("MODIFIED_AFTER_FIRST_FLUSH");
  }

  @Test
  void flushAllSessions_oneSessionFails_otherStillFlushed() {
    String hashA = createCharacterAndSession("Desert");
    String hashB = createCharacterAndSession("Ocean");

    CampaignContext ctxB = campaignCtxtRedis.opsForValue().get(CTX_PREFIX + hashB);
    Long campaignIdB = ctxB.getCampaignId();

    // Corrupt session A's context so flush fails (delete context but keep sorted set entry)
    campaignCtxtRedis.delete(CTX_PREFIX + hashA);

    sessionShutdownHook.flushAllSessions();

    // Session B should still be flushed despite A's issue
    CampaignEntity afterB = campaignRepository.findById(campaignIdB).orElseThrow();
    assertThat(afterB.getCurrentLocation()).isEqualTo("Ocean");
    assertThat(campaignCtxtRedis.hasKey(CTX_PREFIX + hashB)).isFalse();
  }
}
