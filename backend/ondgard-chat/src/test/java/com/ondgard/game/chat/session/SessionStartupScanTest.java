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
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class SessionStartupScanTest extends TestcontainersConfiguration {

  @Autowired private SessionStartupScan sessionStartupScan;
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

  /**
   * Unwrap the @Async proxy so calls run synchronously in the test thread.
   */
  private SessionStartupScan unwrappedScan;

  @BeforeEach
  void setUp() {
    unwrappedScan = AopTestUtils.getTargetObject(sessionStartupScan);

    // Remove stale sessions left by async startup scans from other Spring contexts
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

  private String createCharacterAndSession() {
    String hash = "test-char-" + UUID.randomUUID().toString().substring(0, 20);
    runInTransaction(() ->
        playerCharacterRepository.insertCharacter(POSTMAN_USER_HASH, "ELF", hash, "TestHero", "A test character")
    );
    createdHashes.add(hash);

    CampaignContext ctx = campaignService.load(POSTMAN_USER_HASH, hash);
    ctx.setScene(GameScene.builder().currentLocation("Location-" + hash.substring(0, 8)).build());
    campaignService.save(ctx, hash);

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
  void scanOrphanedSessions_noActiveSessions_completesQuietly() {
    unwrappedScan.scanOrphanedSessions();
  }

  @Test
  void scanOrphanedSessions_allSessionsHaveTimers_noFlush() {
    String hashA = createCharacterAndSession();
    String hashB = createCharacterAndSession();

    assertThat(stringRedisTemplate.hasKey(TIMER_PREFIX + hashA)).isTrue();
    assertThat(stringRedisTemplate.hasKey(TIMER_PREFIX + hashB)).isTrue();

    unwrappedScan.scanOrphanedSessions();

    assertThat(campaignCtxtRedis.hasKey(CTX_PREFIX + hashA)).isTrue();
    assertThat(campaignCtxtRedis.hasKey(CTX_PREFIX + hashB)).isTrue();
    assertThat(stringRedisTemplate.opsForZSet().score(ACTIVE_SESSIONS_KEY, hashA)).isNotNull();
    assertThat(stringRedisTemplate.opsForZSet().score(ACTIVE_SESSIONS_KEY, hashB)).isNotNull();
  }

  @Test
  void scanOrphanedSessions_orphanedSession_getsFlushed() {
    String hash = createCharacterAndSession();
    CampaignContext ctx = campaignCtxtRedis.opsForValue().get(CTX_PREFIX + hash);
    Long campaignId = ctx.getCampaignId();

    stringRedisTemplate.delete(TIMER_PREFIX + hash);

    unwrappedScan.scanOrphanedSessions();

    CampaignEntity entity = campaignRepository.findById(campaignId).orElseThrow();
    assertThat(entity.getCurrentLocation()).isNotNull();

    assertThat(campaignCtxtRedis.hasKey(CTX_PREFIX + hash)).isFalse();
    assertThat(stringRedisTemplate.opsForZSet().score(ACTIVE_SESSIONS_KEY, hash)).isNull();
  }

  @Test
  void scanOrphanedSessions_mixedState_onlyFlushesOrphans() {
    String hashA = createCharacterAndSession();
    String hashB = createCharacterAndSession();

    CampaignContext ctxA = campaignCtxtRedis.opsForValue().get(CTX_PREFIX + hashA);
    Long campaignIdA = ctxA.getCampaignId();

    stringRedisTemplate.delete(TIMER_PREFIX + hashA);

    unwrappedScan.scanOrphanedSessions();

    CampaignEntity entityA = campaignRepository.findById(campaignIdA).orElseThrow();
    assertThat(entityA.getCurrentLocation()).isNotNull();
    assertThat(campaignCtxtRedis.hasKey(CTX_PREFIX + hashA)).isFalse();
    assertThat(stringRedisTemplate.opsForZSet().score(ACTIVE_SESSIONS_KEY, hashA)).isNull();

    assertThat(campaignCtxtRedis.hasKey(CTX_PREFIX + hashB)).isTrue();
    assertThat(stringRedisTemplate.opsForZSet().score(ACTIVE_SESSIONS_KEY, hashB)).isNotNull();
  }
}
