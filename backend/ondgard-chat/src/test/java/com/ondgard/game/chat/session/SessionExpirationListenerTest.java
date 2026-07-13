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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class SessionExpirationListenerTest extends TestcontainersConfiguration {

  @Autowired private SessionExpirationListener listener;
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

  @Test
  void onMessage_timerKeyExpired_flushesAndCleansRedis() {
    String hash = createCharacterHash();
    CampaignContext ctx = campaignService.load(POSTMAN_USER_HASH, hash);
    Long campaignId = ctx.getCampaignId();

    ctx.setScene(GameScene.builder().currentLocation("Battlefield").build());
    campaignService.save(ctx, hash);

    stringRedisTemplate.delete(TIMER_PREFIX + hash);

    Message message = new DefaultMessage(
        "__keyevent@0__:expired".getBytes(),
        (TIMER_PREFIX + hash).getBytes()
    );
    listener.onMessage(message, null);

    CampaignEntity entity = campaignRepository.findById(campaignId).orElseThrow();
    assertThat(entity.getCurrentLocation()).isEqualTo("Battlefield");

    assertThat(campaignCtxtRedis.hasKey(CTX_PREFIX + hash)).isFalse();
    assertThat(stringRedisTemplate.opsForZSet().score(ACTIVE_SESSIONS_KEY, hash)).isNull();
  }

  @Test
  void onMessage_nonTimerKey_isIgnored() {
    Message message = new DefaultMessage(
        "__keyevent@0__:expired".getBytes(),
        "some-other-key".getBytes()
    );

    listener.onMessage(message, null);
  }

  @Test
  void onMessage_alreadyClaimed_noOp() {
    String hash = createCharacterHash();
    CampaignContext ctx = campaignService.load(POSTMAN_USER_HASH, hash);
    Long campaignId = ctx.getCampaignId();

    ctx.setScene(GameScene.builder().currentLocation("Harbor").build());
    campaignService.save(ctx, hash);

    stringRedisTemplate.opsForZSet().remove(ACTIVE_SESSIONS_KEY, hash);

    Message message = new DefaultMessage(
        "__keyevent@0__:expired".getBytes(),
        (TIMER_PREFIX + hash).getBytes()
    );
    listener.onMessage(message, null);

    CampaignEntity entity = campaignRepository.findById(campaignId).orElseThrow();
    assertThat(entity.getCurrentLocation()).isNull();

    assertThat(campaignCtxtRedis.hasKey(CTX_PREFIX + hash)).isTrue();
  }
}
