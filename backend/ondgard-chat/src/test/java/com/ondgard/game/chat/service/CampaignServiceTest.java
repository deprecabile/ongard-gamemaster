package com.ondgard.game.chat.service;

import com.google.gson.Gson;
import com.ondgard.game.GameGsonFactory;
import com.ondgard.game.chat.TestcontainersConfiguration;
import com.ondgard.game.chat.contract.CampaignListItem;
import com.ondgard.game.chat.entity.AdventureLogEntity;
import com.ondgard.game.chat.entity.CampaignEntity;
import com.ondgard.game.chat.entity.CampaignQuestlogEntity;
import com.ondgard.game.chat.entity.PlayerInventoryEntity;
import com.ondgard.game.chat.model.CampaignContext;
import com.ondgard.game.chat.model.ChatEntry;
import com.ondgard.game.chat.model.GameScene;
import com.ondgard.game.chat.model.SummaryPendingResult;
import com.ondgard.game.chat.model.inventory.*;
import com.ondgard.game.chat.repository.*;
import com.ondgard.game.exception.ForbiddenException;
import com.ondgard.game.header.GameUserHeader;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@TestPropertySource( properties = {
    "ondgard.session.flush-every-n-turns=3",
    "ondgard.session.inactivity-timeout=5s"
} )
class CampaignServiceTest extends TestcontainersConfiguration {

  @Autowired private CampaignService campaignService;
  @Autowired private CampaignRepository campaignRepository;
  @Autowired private PlayerCharacterRepository playerCharacterRepository;
  @Autowired private AdventureLogRepository adventureLogRepository;
  @Autowired private CampaignQuestlogRepository campaignQuestlogRepository;
  @Autowired private PlayerInventoryRepository playerInventoryRepository;
  @Autowired private RedisTemplate<String, CampaignContext> campaignCtxtRedis;
  @Autowired private StringRedisTemplate stringRedisTemplate;
  @Autowired private PlatformTransactionManager txManager;
  @Autowired private EntityManager em;

  private final Gson gson = GameGsonFactory.build();

  private static final UUID POSTMAN_USER_HASH = UUID.fromString("997a7552-5c1b-42c6-a0c0-094c50a0ad53");
  private static final String CTX_PREFIX = "ondgard:ctx:";
  private static final String TIMER_PREFIX = "ondgard:session-timer:";
  private static final String ACTIVE_SESSIONS_KEY = "ondgard:active-sessions";
  private static final String PENDING_PREFIX = "ondgard:summary-pending:";

  private final List<String> createdHashes = new ArrayList<>();

  @AfterEach
  void cleanup() {
    for( String hash : createdHashes ){
      cleanupRedisKeys(hash);
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

  private void cleanupRedisKeys(String characterHash) {
    campaignCtxtRedis.delete(CTX_PREFIX + characterHash);
    stringRedisTemplate.delete(TIMER_PREFIX + characterHash);
    stringRedisTemplate.delete(PENDING_PREFIX + characterHash);
    stringRedisTemplate.opsForZSet().remove(ACTIVE_SESSIONS_KEY, characterHash);
  }

  private void cleanupDb(String characterHash) {
    runInTransaction(() -> {
      em.createNativeQuery(
              "DELETE FROM adventure_log WHERE campaign_id IN " +
                  "(SELECT c.character_id FROM campaign c JOIN player_character pc ON pc.id = c.character_id WHERE pc.character_hash = :hash)")
          .setParameter("hash", characterHash)
          .executeUpdate();
      em.createNativeQuery(
              "DELETE FROM campaign_questlog WHERE campaign_id IN " +
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

  // ************************* load tests *************************

  @Test
  void load_firstTurn_createsCampaignAndCachesInRedis() {
    String hash = createCharacterHash();

    CampaignContext ctx = campaignService.load(POSTMAN_USER_HASH, hash);

    assertThat(ctx).isNotNull();
    assertThat(ctx.getCampaignId()).isNotNull();
    assertThat(ctx.getCurrentTurn()).isZero();
    assertThat(ctx.getRecentHistory()).isEmpty();

    CampaignContext cached = campaignCtxtRedis.opsForValue().get(CTX_PREFIX + hash);
    assertThat(cached).isNotNull();
    assertThat(cached.getCampaignId()).isEqualTo(ctx.getCampaignId());

    Double score = stringRedisTemplate.opsForZSet().score(ACTIVE_SESSIONS_KEY, hash);
    assertThat(score).isNotNull();

    Boolean timerExists = stringRedisTemplate.hasKey(TIMER_PREFIX + hash);
    assertThat(timerExists).isTrue();
  }

  @Test
  void load_cacheHit_returnsCachedContext() {
    String hash = createCharacterHash();
    CampaignContext ctx = campaignService.load(POSTMAN_USER_HASH, hash);

    ctx.setScene(GameScene.builder().currentLocation("CACHE_MARKER").build());
    campaignCtxtRedis.opsForValue().set(CTX_PREFIX + hash, ctx);

    CampaignContext reloaded = campaignService.load(POSTMAN_USER_HASH, hash);

    assertThat(reloaded.getScene().getCurrentLocation()).isEqualTo("CACHE_MARKER");
  }

  @Test
  void load_cacheMiss_dbHit_loadsFromDb() {
    String hash = createCharacterHash();
    CampaignContext ctx = campaignService.load(POSTMAN_USER_HASH, hash);
    Long campaignId = ctx.getCampaignId();

    runInTransaction(() -> {
      CampaignEntity entity = campaignRepository.findById(campaignId).orElseThrow();
      entity.setCurrentLocation("DB_LOCATION");
      entity.setTurnCount(5);
      campaignRepository.save(entity);
    });

    campaignCtxtRedis.delete(CTX_PREFIX + hash);

    CampaignContext reloaded = campaignService.load(POSTMAN_USER_HASH, hash);

    assertThat(reloaded.getScene().getCurrentLocation()).isEqualTo("DB_LOCATION");
    assertThat(reloaded.getCurrentTurn()).isEqualTo(5);

    CampaignContext cached = campaignCtxtRedis.opsForValue().get(CTX_PREFIX + hash);
    assertThat(cached).isNotNull();
    assertThat(cached.getScene().getCurrentLocation()).isEqualTo("DB_LOCATION");
  }

  @Test
  void load_cacheMiss_dbHit_loadsRecentHistory() {
    String hash = createCharacterHash();
    CampaignContext ctx = campaignService.load(POSTMAN_USER_HASH, hash);
    Long campaignId = ctx.getCampaignId();

    runInTransaction(() -> {
      CampaignEntity campaign = em.find(CampaignEntity.class, campaignId);
      for( int i = 1; i <= 3; i++ ){
        adventureLogRepository.save(AdventureLogEntity.builder()
            .campaign(campaign).role("USER").content("action-" + i).turnNumber(i).build());
        adventureLogRepository.save(AdventureLogEntity.builder()
            .campaign(campaign).role("GM").content("response-" + i).turnNumber(i).build());
      }
    });

    campaignCtxtRedis.delete(CTX_PREFIX + hash);

    CampaignContext reloaded = campaignService.load(POSTMAN_USER_HASH, hash);

    assertThat(reloaded.getRecentHistory()).hasSize(3);
    final Iterator<ChatEntry> itr = reloaded.getRecentHistory().iterator();
    assertThat(itr.next().turnNumber()).isEqualTo(1);
    assertThat(itr.next().turnNumber()).isEqualTo(2);
    assertThat(itr.next().turnNumber()).isEqualTo(3);
  }

  @Test
  void load_cacheMiss_dbHit_loadsQuestLog() {
    String hash = createCharacterHash();
    CampaignContext ctx = campaignService.load(POSTMAN_USER_HASH, hash);
    Long campaignId = ctx.getCampaignId();

    runInTransaction(() -> {
      CampaignEntity campaign = em.find(CampaignEntity.class, campaignId);
      campaignQuestlogRepository.save(CampaignQuestlogEntity.builder()
          .campaign(campaign)
          .turnNumber(1)
          .questActive("Save the village")
          .questCompleted("Found the map")
          .build());
    });

    campaignCtxtRedis.delete(CTX_PREFIX + hash);

    CampaignContext reloaded = campaignService.load(POSTMAN_USER_HASH, hash);

    assertThat(reloaded.getQuestLog()).isNotNull();
    assertThat(reloaded.getQuestLog().turnNumber()).isEqualTo(1);
    assertThat(reloaded.getQuestLog().questActive()).isEqualTo("Save the village");
    assertThat(reloaded.getQuestLog().questCompleted()).isEqualTo("Found the map");
  }

  @Test
  void load_touchesSession() {
    String hash = createCharacterHash();

    campaignService.load(POSTMAN_USER_HASH, hash);

    Double score = stringRedisTemplate.opsForZSet().score(ACTIVE_SESSIONS_KEY, hash);
    assertThat(score).isNotNull();

    Boolean timerExists = stringRedisTemplate.hasKey(TIMER_PREFIX + hash);
    assertThat(timerExists).isTrue();

    Long ttl = stringRedisTemplate.getExpire(TIMER_PREFIX + hash);
    assertThat(ttl).isGreaterThan(0);
  }

  // ************************* save tests *************************

  @Test
  void save_incrementsTurnsSinceLastFlush() {
    String hash = createCharacterHash();
    CampaignContext ctx = campaignService.load(POSTMAN_USER_HASH, hash);

    campaignService.save(ctx, hash);
    CampaignContext after1 = campaignCtxtRedis.opsForValue().get(CTX_PREFIX + hash);
    assertThat(after1.getTurnsSinceLastFlush()).isEqualTo(1);

    campaignService.save(ctx, hash);
    CampaignContext after2 = campaignCtxtRedis.opsForValue().get(CTX_PREFIX + hash);
    assertThat(after2.getTurnsSinceLastFlush()).isEqualTo(2);
  }

  @Test
  void save_belowThreshold_doesNotFlushToDb() {
    String hash = createCharacterHash();
    CampaignContext ctx = campaignService.load(POSTMAN_USER_HASH, hash);
    Long campaignId = ctx.getCampaignId();

    ctx.setScene(GameScene.builder().currentLocation("New Forest").build());
    campaignService.save(ctx, hash);

    CampaignEntity entity = campaignRepository.findById(campaignId).orElseThrow();
    assertThat(entity.getCurrentLocation()).isNull();
  }

  @Test
  void save_atThreshold_flushesToDbAndResetsCounter() {
    String hash = createCharacterHash();
    CampaignContext ctx = campaignService.load(POSTMAN_USER_HASH, hash);
    Long campaignId = ctx.getCampaignId();

    ctx.setScene(GameScene.builder().currentLocation("Dark Forest").build());
    ctx.setNarrativeSummary("A hero's journey begins.");

    campaignService.save(ctx, hash); // 1
    campaignService.save(ctx, hash); // 2
    campaignService.save(ctx, hash); // 3 → flush

    CampaignEntity entity = campaignRepository.findById(campaignId).orElseThrow();
    assertThat(entity.getCurrentLocation()).isEqualTo("Dark Forest");
    assertThat(entity.getNarrativeSummary()).isEqualTo("A hero's journey begins.");

    CampaignContext cached = campaignCtxtRedis.opsForValue().get(CTX_PREFIX + hash);
    assertThat(cached.getTurnsSinceLastFlush()).isZero();
  }

  // ************************* flush tests *************************

  @Test
  void flush_writesToDbAndCleansAllRedisKeys() {
    String hash = createCharacterHash();
    CampaignContext ctx = campaignService.load(POSTMAN_USER_HASH, hash);
    Long campaignId = ctx.getCampaignId();

    ctx.setScene(GameScene.builder().currentLocation("Mountain Peak").build());
    ctx.setNarrativeSummary("Climbed the mountain.");
    campaignService.save(ctx, hash);

    campaignService.flush(hash);

    CampaignEntity entity = campaignRepository.findById(campaignId).orElseThrow();
    assertThat(entity.getCurrentLocation()).isEqualTo("Mountain Peak");
    assertThat(entity.getNarrativeSummary()).isEqualTo("Climbed the mountain.");

    assertThat(campaignCtxtRedis.hasKey(CTX_PREFIX + hash)).isFalse();
    assertThat(stringRedisTemplate.hasKey(TIMER_PREFIX + hash)).isFalse();
    assertThat(stringRedisTemplate.opsForZSet().score(ACTIVE_SESSIONS_KEY, hash)).isNull();
  }

  @Test
  void flush_noContextInRedis_cleansKeysWithoutError() {
    String hash = createCharacterHash();
    campaignService.load(POSTMAN_USER_HASH, hash);

    campaignCtxtRedis.delete(CTX_PREFIX + hash);

    campaignService.flush(hash);

    assertThat(stringRedisTemplate.hasKey(TIMER_PREFIX + hash)).isFalse();
    assertThat(stringRedisTemplate.opsForZSet().score(ACTIVE_SESSIONS_KEY, hash)).isNull();
  }

  // ************************* claimAndFlush tests *************************

  @Test
  void claimAndFlush_succeeds_whenSessionExists() {
    String hash = createCharacterHash();
    CampaignContext ctx = campaignService.load(POSTMAN_USER_HASH, hash);
    Long campaignId = ctx.getCampaignId();

    ctx.setScene(GameScene.builder().currentLocation("River Crossing").build());
    campaignService.save(ctx, hash);

    campaignService.claimAndFlush(hash);

    CampaignEntity entity = campaignRepository.findById(campaignId).orElseThrow();
    assertThat(entity.getCurrentLocation()).isEqualTo("River Crossing");

    assertThat(campaignCtxtRedis.hasKey(CTX_PREFIX + hash)).isFalse();
    assertThat(stringRedisTemplate.opsForZSet().score(ACTIVE_SESSIONS_KEY, hash)).isNull();
  }

  @Test
  void claimAndFlush_skips_whenAlreadyClaimed() {
    String hash = createCharacterHash();
    CampaignContext ctx = campaignService.load(POSTMAN_USER_HASH, hash);
    Long campaignId = ctx.getCampaignId();

    ctx.setScene(GameScene.builder().currentLocation("Cave Entrance").build());
    campaignService.save(ctx, hash);

    stringRedisTemplate.opsForZSet().remove(ACTIVE_SESSIONS_KEY, hash);

    campaignService.claimAndFlush(hash);

    CampaignEntity entity = campaignRepository.findById(campaignId).orElseThrow();
    assertThat(entity.getCurrentLocation()).isNull();

    assertThat(campaignCtxtRedis.hasKey(CTX_PREFIX + hash)).isTrue();
  }

  // ************************* Redis inventory round-trip *************************

  @Test
  void redisRoundTrip_inventoryWithNotNullTreeSet_survivesJacksonSerialization() {
    String hash = createCharacterHash();
    CampaignContext ctx = campaignService.load(POSTMAN_USER_HASH, hash);

    Inventory inventory = Inventory.builder()
        .money(50013)
        .mount(List.of(
            Mount.builder().nome("Cavallo da guerra").tipo("cavalcatura").stato("sano").build()
        ))
        .contenitori(List.of(
            Contenitore.builder()
                .nome("Zaino")
                .oggetti(List.of(
                    InventoryItem.builder().nome("Spada lunga").descrizione("Acciaio elfico").quantita(1).peso(3.0)
                        .extra(List.of(ExtraProperty.builder().chiave("enchanted").valore("true").build()))
                        .build(),
                    InventoryItem.builder().nome("Pozione di cura").quantita(5).peso(0.5).build()
                ))
                .build()
        ))
        .build();
    ctx.setInventory(inventory);

    campaignCtxtRedis.opsForValue().set(CTX_PREFIX + hash, ctx);

    CampaignContext cached = campaignCtxtRedis.opsForValue().get(CTX_PREFIX + hash);

    assertThat(cached).isNotNull();
    assertThat(cached.getInventory()).isNotNull();
    assertThat(cached.getInventory().getMoney()).isEqualTo(50013);
    assertThat(cached.getInventory().getMount()).hasSize(1);
    assertThat(cached.getInventory().getContenitori()).hasSize(1);

    Contenitore zaino = cached.getInventory().getContenitori().iterator().next();
    assertThat(zaino.getNome()).isEqualTo("Zaino");
    assertThat(zaino.getOggetti()).hasSize(2);

    InventoryItem spada = zaino.getOggetti().iterator().next();
    assertThat(spada.getExtra()).hasSize(1);
    assertThat(spada.getExtra().iterator().next().getChiave()).isEqualTo("enchanted");
  }

  @Test
  void redisRoundTrip_inventoryEmpty_survivesJacksonSerialization() {
    String hash = createCharacterHash();
    CampaignContext ctx = campaignService.load(POSTMAN_USER_HASH, hash);

    ctx.setInventory(Inventory.empty());

    campaignCtxtRedis.opsForValue().set(CTX_PREFIX + hash, ctx);

    CampaignContext cached = campaignCtxtRedis.opsForValue().get(CTX_PREFIX + hash);

    assertThat(cached).isNotNull();
    assertThat(cached.getInventory()).isNotNull();
    assertThat(cached.getInventory().getMoney()).isZero();
    assertThat(cached.getInventory().getContenitori()).hasSize(2);
    assertThat(cached.getInventory().getContenitori().iterator().next().getNome()).isEqualTo("backpack");
  }

  // ************************* saveContextOnly tests *************************

  @Test
  void saveContextOnly_doesNotIncrementFlushCounter() {
    String hash = createCharacterHash();
    CampaignContext ctx = campaignService.load(POSTMAN_USER_HASH, hash);

    assertThat(ctx.getTurnsSinceLastFlush()).isZero();

    campaignService.saveContextOnly(ctx, hash);
    campaignService.saveContextOnly(ctx, hash);
    campaignService.saveContextOnly(ctx, hash);

    CampaignContext cached = campaignCtxtRedis.opsForValue().get(CTX_PREFIX + hash);
    assertThat(cached).isNotNull();
    assertThat(cached.getTurnsSinceLastFlush()).isZero();
  }

  @Test
  void saveContextOnly_doesNotFlushToDb() {
    String hash = createCharacterHash();
    CampaignContext ctx = campaignService.load(POSTMAN_USER_HASH, hash);
    Long campaignId = ctx.getCampaignId();

    ctx.setScene(GameScene.builder().currentLocation("Advisor Location").build());
    campaignService.saveContextOnly(ctx, hash);

    CampaignEntity entity = campaignRepository.findById(campaignId).orElseThrow();
    assertThat(entity.getCurrentLocation()).isNull();
  }

  @Test
  void saveContextOnly_updatesLastUpdate() {
    String hash = createCharacterHash();
    CampaignContext ctx = campaignService.load(POSTMAN_USER_HASH, hash);

    LocalDateTime before = LocalDateTime.now();
    campaignService.saveContextOnly(ctx, hash);

    CampaignContext cached = campaignCtxtRedis.opsForValue().get(CTX_PREFIX + hash);
    assertThat(cached).isNotNull();
    assertThat(cached.getLastUpdate()).isAfterOrEqualTo(before);
  }

  // ************************* advisorFacts Redis round-trip *************************

  @Test
  void redisRoundTrip_advisorFacts_survivesSerialization() {
    String hash = createCharacterHash();
    CampaignContext ctx = campaignService.load(POSTMAN_USER_HASH, hash);

    ctx.getAdvisorFacts().add("Il taverniere si chiama Gundren");
    ctx.getAdvisorFacts().add("La spada e' di fattura nanica");
    campaignCtxtRedis.opsForValue().set(CTX_PREFIX + hash, ctx);

    CampaignContext cached = campaignCtxtRedis.opsForValue().get(CTX_PREFIX + hash);

    assertThat(cached).isNotNull();
    assertThat(cached.getAdvisorFacts()).containsExactly(
        "Il taverniere si chiama Gundren",
        "La spada e' di fattura nanica"
    );
  }

  @Test
  void redisRoundTrip_emptyAdvisorFacts_deserializesToEmptyList() {
    String hash = createCharacterHash();
    CampaignContext ctx = campaignService.load(POSTMAN_USER_HASH, hash);

    campaignCtxtRedis.opsForValue().set(CTX_PREFIX + hash, ctx);

    CampaignContext cached = campaignCtxtRedis.opsForValue().get(CTX_PREFIX + hash);

    assertThat(cached).isNotNull();
    assertThat(cached.getAdvisorFacts()).isNotNull().isEmpty();
  }

  // ************************* lastUpdate tests *************************

  @Test
  void load_fromDb_setsLastUpdateFromEntityUpdated() {
    String hash = createCharacterHash();
    CampaignContext ctx = campaignService.load(POSTMAN_USER_HASH, hash);
    Long campaignId = ctx.getCampaignId();

    // Clear Redis so next load comes from DB
    campaignCtxtRedis.delete(CTX_PREFIX + hash);

    CampaignContext reloaded = campaignService.load(POSTMAN_USER_HASH, hash);

    CampaignEntity entity = campaignRepository.findById(campaignId).orElseThrow();
    assertThat(reloaded.getLastUpdate()).isEqualTo(entity.getUpdated());
  }

  @Test
  void save_updatesLastUpdate() {
    String hash = createCharacterHash();
    CampaignContext ctx = campaignService.load(POSTMAN_USER_HASH, hash);

    LocalDateTime before = LocalDateTime.now();
    campaignService.save(ctx, hash);

    CampaignContext cached = campaignCtxtRedis.opsForValue().get(CTX_PREFIX + hash);
    LocalDateTime after = LocalDateTime.now();
    assertThat(cached).isNotNull();
    assertThat(cached.getLastUpdate()).isNotNull();
    assertThat(cached.getLastUpdate()).isAfterOrEqualTo(before);
    assertThat(cached.getLastUpdate()).isBeforeOrEqualTo(after);
  }

  // ************************* flushToDb field mapping *************************

  @Test
  void flushToDb_mapsAllFieldsCorrectly() {
    String hash = createCharacterHash();
    CampaignContext ctx = campaignService.load(POSTMAN_USER_HASH, hash);
    Long campaignId = ctx.getCampaignId();

    ctx.setNarrativeSummary("The saga continues...");
    ctx.setSummaryVersion(2);
    ctx.setCurrentTurn(10);
    ctx.setTurnsSinceLastSummary(3);
    ctx.setScene(GameScene.builder()
        .currentLocation("Ancient Temple")
        .gameDate("15 Solara 1247")
        .gameTime("Midnight")
        .meteo("Stormy")
        .temperature("Cold")
        .build());

    campaignCtxtRedis.opsForValue().set(CTX_PREFIX + hash, ctx);

    campaignService.flush(hash);

    CampaignEntity entity = campaignRepository.findById(campaignId).orElseThrow();
    assertThat(entity.getNarrativeSummary()).isEqualTo("The saga continues...");
    assertThat(entity.getSummaryVersion()).isEqualTo(2);
    assertThat(entity.getTurnCount()).isEqualTo(10);
    assertThat(entity.getLastSummaryAtTurn()).isEqualTo(7); // 10 - 3
    assertThat(entity.getCurrentLocation()).isEqualTo("Ancient Temple");
    assertThat(entity.getGameDate()).isEqualTo("15 Solara 1247");
    assertThat(entity.getGameTime()).isEqualTo("Midnight");
    assertThat(entity.getMeteo()).isEqualTo("Stormy");
    assertThat(entity.getTemperature()).isEqualTo("Cold");
  }

  // ************************* applyPendingSummary tests *************************

  @Test
  void load_cacheHit_appliesPendingSummary() {
    String hash = createCharacterHash();
    CampaignContext ctx = campaignService.load(POSTMAN_USER_HASH, hash);
    ctx.setSummaryVersion(0);
    campaignCtxtRedis.opsForValue().set(CTX_PREFIX + hash, ctx);

    var pending = new SummaryPendingResult("The hero traveled far.", 1);
    stringRedisTemplate.opsForValue().set(PENDING_PREFIX + hash,
        gson.toJson(pending));

    CampaignContext reloaded = campaignService.load(POSTMAN_USER_HASH, hash);

    assertThat(reloaded.getNarrativeSummary()).isEqualTo("The hero traveled far.");
    assertThat(reloaded.getSummaryVersion()).isEqualTo(1);
    assertThat(stringRedisTemplate.hasKey(PENDING_PREFIX + hash)).isFalse();
  }

  @Test
  void load_cacheHit_stalePendingSummary_notApplied() {
    String hash = createCharacterHash();
    CampaignContext ctx = campaignService.load(POSTMAN_USER_HASH, hash);
    ctx.setSummaryVersion(3);
    ctx.setNarrativeSummary("Current summary.");
    campaignCtxtRedis.opsForValue().set(CTX_PREFIX + hash, ctx);

    var pending = new SummaryPendingResult("Stale summary.", 2);
    stringRedisTemplate.opsForValue().set(PENDING_PREFIX + hash,
        gson.toJson(pending));

    CampaignContext reloaded = campaignService.load(POSTMAN_USER_HASH, hash);

    assertThat(reloaded.getNarrativeSummary()).isEqualTo("Current summary.");
    assertThat(reloaded.getSummaryVersion()).isEqualTo(3);
    assertThat(stringRedisTemplate.hasKey(PENDING_PREFIX + hash)).isFalse();
  }

  @Test
  void load_dbHit_appliesPendingSummary() {
    String hash = createCharacterHash();
    CampaignContext ctx = campaignService.load(POSTMAN_USER_HASH, hash);
    campaignCtxtRedis.delete(CTX_PREFIX + hash);

    var pending = new SummaryPendingResult("DB path summary.", 1);
    stringRedisTemplate.opsForValue().set(PENDING_PREFIX + hash,
        gson.toJson(pending));

    CampaignContext reloaded = campaignService.load(POSTMAN_USER_HASH, hash);

    assertThat(reloaded.getNarrativeSummary()).isEqualTo("DB path summary.");
    assertThat(reloaded.getSummaryVersion()).isEqualTo(1);
    assertThat(stringRedisTemplate.hasKey(PENDING_PREFIX + hash)).isFalse();
  }

  @Test
  void flush_appliesPendingSummaryBeforeFlush() {
    String hash = createCharacterHash();
    CampaignContext ctx = campaignService.load(POSTMAN_USER_HASH, hash);
    Long campaignId = ctx.getCampaignId();

    ctx.setScene(GameScene.builder().currentLocation("Castle").build());
    campaignService.save(ctx, hash);

    var pending = new SummaryPendingResult("Flushed summary.", 1);
    stringRedisTemplate.opsForValue().set(PENDING_PREFIX + hash,
        gson.toJson(pending));

    campaignService.flush(hash);

    CampaignEntity entity = campaignRepository.findById(campaignId).orElseThrow();
    assertThat(entity.getNarrativeSummary()).isEqualTo("Flushed summary.");
    assertThat(entity.getSummaryVersion()).isEqualTo(1);
    assertThat(stringRedisTemplate.hasKey(PENDING_PREFIX + hash)).isFalse();
  }

  @Test
  void claimAndFlush_appliesPendingSummaryBeforeFlush() {
    String hash = createCharacterHash();
    CampaignContext ctx = campaignService.load(POSTMAN_USER_HASH, hash);
    Long campaignId = ctx.getCampaignId();

    ctx.setScene(GameScene.builder().currentLocation("Tower").build());
    campaignService.save(ctx, hash);

    var pending = new SummaryPendingResult("Claimed summary.", 1);
    stringRedisTemplate.opsForValue().set(PENDING_PREFIX + hash,
        gson.toJson(pending));

    campaignService.claimAndFlush(hash);

    CampaignEntity entity = campaignRepository.findById(campaignId).orElseThrow();
    assertThat(entity.getNarrativeSummary()).isEqualTo("Claimed summary.");
    assertThat(entity.getSummaryVersion()).isEqualTo(1);
    assertThat(stringRedisTemplate.hasKey(PENDING_PREFIX + hash)).isFalse();
  }

  // ************************* rawSummaryBuffer tests *************************

  @Test
  void load_dbHit_rebuildsRawBuffer() {
    String hash = createCharacterHash();
    CampaignContext ctx = campaignService.load(POSTMAN_USER_HASH, hash);
    Long campaignId = ctx.getCampaignId();

    // Create 15 turns, summaryAtTurn=0, recentHistorySize=10
    // Buffer should be turns 1-5 (15 - 10 = 5, so range 1..5)
    runInTransaction(() -> {
      CampaignEntity campaign = em.find(CampaignEntity.class, campaignId);
      campaign.setTurnCount(15);
      campaign.setLastSummaryAtTurn(0);
      campaignRepository.save(campaign);
      for( int i = 1; i <= 15; i++ ){
        adventureLogRepository.save(AdventureLogEntity.builder()
            .campaign(campaign).role("USER").content("action-" + i).turnNumber(i).build());
        adventureLogRepository.save(AdventureLogEntity.builder()
            .campaign(campaign).role("GM").content("response-" + i).turnNumber(i).build());
      }
    });

    campaignCtxtRedis.delete(CTX_PREFIX + hash);

    CampaignContext reloaded = campaignService.load(POSTMAN_USER_HASH, hash);

    assertThat(reloaded.getRawSummaryBuffer()).isNotNull().hasSize(5);
    assertThat(reloaded.getRawSummaryBuffer().getFirst().turnNumber()).isEqualTo(1);
    assertThat(reloaded.getRawSummaryBuffer().getLast().turnNumber()).isEqualTo(5);
    assertThat(reloaded.getRawSummaryBuffer().getFirst().userMessage()).isEqualTo("action-1");
    assertThat(reloaded.getRawSummaryBuffer().getLast().gmResponse()).isEqualTo("response-5");
  }

  @Test
  void load_dbHit_noBufferNeeded_returnsEmptyBuffer() {
    String hash = createCharacterHash();
    CampaignContext ctx = campaignService.load(POSTMAN_USER_HASH, hash);
    Long campaignId = ctx.getCampaignId();

    // Only 5 turns, summaryAtTurn=0, recentHistorySize=10
    // bufferEnd = 5 - 10 = -5 → no buffer needed
    runInTransaction(() -> {
      CampaignEntity campaign = em.find(CampaignEntity.class, campaignId);
      campaign.setTurnCount(5);
      campaign.setLastSummaryAtTurn(0);
      campaignRepository.save(campaign);
      for( int i = 1; i <= 5; i++ ){
        adventureLogRepository.save(AdventureLogEntity.builder()
            .campaign(campaign).role("USER").content("action-" + i).turnNumber(i).build());
        adventureLogRepository.save(AdventureLogEntity.builder()
            .campaign(campaign).role("GM").content("response-" + i).turnNumber(i).build());
      }
    });

    campaignCtxtRedis.delete(CTX_PREFIX + hash);

    CampaignContext reloaded = campaignService.load(POSTMAN_USER_HASH, hash);

    assertThat(reloaded.getRawSummaryBuffer()).isEmpty();
  }

  @Test
  void load_dbHit_rebuildsRawBuffer_afterPreviousSummary() {
    String hash = createCharacterHash();
    CampaignContext ctx = campaignService.load(POSTMAN_USER_HASH, hash);
    Long campaignId = ctx.getCampaignId();

    // 25 turns, lastSummaryAtTurn=10, recentHistorySize=10
    // Buffer should be turns 11-15 (start=10+1=11, end=25-10=15)
    runInTransaction(() -> {
      CampaignEntity campaign = em.find(CampaignEntity.class, campaignId);
      campaign.setTurnCount(25);
      campaign.setLastSummaryAtTurn(10);
      campaign.setNarrativeSummary("Previous summary.");
      campaign.setSummaryVersion(1);
      campaignRepository.save(campaign);
      for( int i = 1; i <= 25; i++ ){
        adventureLogRepository.save(AdventureLogEntity.builder()
            .campaign(campaign).role("USER").content("action-" + i).turnNumber(i).build());
        adventureLogRepository.save(AdventureLogEntity.builder()
            .campaign(campaign).role("GM").content("response-" + i).turnNumber(i).build());
      }
    });

    campaignCtxtRedis.delete(CTX_PREFIX + hash);

    CampaignContext reloaded = campaignService.load(POSTMAN_USER_HASH, hash);

    assertThat(reloaded.getRawSummaryBuffer()).hasSize(5);
    assertThat(reloaded.getRawSummaryBuffer().getFirst().turnNumber()).isEqualTo(11);
    assertThat(reloaded.getRawSummaryBuffer().getLast().turnNumber()).isEqualTo(15);
  }

  @Test
  void load_cacheHit_appliesPendingSummary_reSavesToRedis() {
    String hash = createCharacterHash();
    CampaignContext ctx = campaignService.load(POSTMAN_USER_HASH, hash);
    ctx.setSummaryVersion(0);
    campaignCtxtRedis.opsForValue().set(CTX_PREFIX + hash, ctx);

    var pending = new SummaryPendingResult("Persisted summary.", 1);
    stringRedisTemplate.opsForValue().set(PENDING_PREFIX + hash,
        gson.toJson(pending));

    campaignService.load(POSTMAN_USER_HASH, hash);

    // Verify updated context was persisted back to Redis cache
    CampaignContext cached = campaignCtxtRedis.opsForValue().get(CTX_PREFIX + hash);
    assertThat(cached).isNotNull();
    assertThat(cached.getNarrativeSummary()).isEqualTo("Persisted summary.");
    assertThat(cached.getSummaryVersion()).isEqualTo(1);
  }

  @Test
  void load_cacheHit_malformedPendingJson_cleansUpWithoutError() {
    String hash = createCharacterHash();
    CampaignContext ctx = campaignService.load(POSTMAN_USER_HASH, hash);
    ctx.setSummaryVersion(0);
    ctx.setNarrativeSummary("Original.");
    campaignCtxtRedis.opsForValue().set(CTX_PREFIX + hash, ctx);

    // Write malformed JSON as pending
    stringRedisTemplate.opsForValue().set(PENDING_PREFIX + hash, "not valid json{{{");

    CampaignContext reloaded = campaignService.load(POSTMAN_USER_HASH, hash);

    // Original values preserved
    assertThat(reloaded.getNarrativeSummary()).isEqualTo("Original.");
    assertThat(reloaded.getSummaryVersion()).isEqualTo(0);
    // Pending key cleaned up
    assertThat(stringRedisTemplate.hasKey(PENDING_PREFIX + hash)).isFalse();
  }

  @Test
  void redisRoundTrip_rawSummaryBuffer_survivesSerialization() {
    String hash = createCharacterHash();
    CampaignContext ctx = campaignService.load(POSTMAN_USER_HASH, hash);

    ctx.setRawSummaryBuffer(new ArrayList<>(List.of(new ChatEntry(1, "explore", "found cave"))));
    campaignCtxtRedis.opsForValue().set(CTX_PREFIX + hash, ctx);

    CampaignContext cached = campaignCtxtRedis.opsForValue().get(CTX_PREFIX + hash);

    assertThat(cached).isNotNull();
    assertThat(cached.getRawSummaryBuffer()).hasSize(1);
    assertThat(cached.getRawSummaryBuffer().getFirst().turnNumber()).isEqualTo(1);
    assertThat(cached.getRawSummaryBuffer().getFirst().userMessage()).isEqualTo("explore");
    assertThat(cached.getRawSummaryBuffer().getFirst().gmResponse()).isEqualTo("found cave");
  }

  // ************************* hasCampaigns tests *************************

  @Test
  void hasCampaigns_returnsTrue_whenCampaignWithTurnsExists() {
    String hash = createCharacterHash();
    CampaignContext ctx = campaignService.load(POSTMAN_USER_HASH, hash);
    Long campaignId = ctx.getCampaignId();

    runInTransaction(() -> {
      CampaignEntity entity = campaignRepository.findById(campaignId).orElseThrow();
      entity.setTurnCount(3);
      campaignRepository.save(entity);
    });

    assertThat(campaignService.hasCampaigns(buildUserHeader())).isTrue();
  }

  @Test
  void hasCampaigns_returnsFalse_whenOnlyZeroTurnCampaigns() {
    String hash = createCharacterHash();
    campaignService.load(POSTMAN_USER_HASH, hash);

    assertThat(campaignService.hasCampaigns(buildUserHeader())).isFalse();
  }

  @Test
  void hasCampaigns_returnsFalse_whenNoCampaigns() {
    GameUserHeader otherUser = GameUserHeader.builder()
        .userId("00000000-0000-0000-0000-000000000099")
        .username("nobody")
        .build();

    assertThat(campaignService.hasCampaigns(otherUser)).isFalse();
  }

  // ************************* listCampaigns tests *************************

  private GameUserHeader buildUserHeader() {
    return GameUserHeader.builder()
        .userId(POSTMAN_USER_HASH.toString())
        .username("postman")
        .build();
  }

  @Test
  void listCampaigns_returnsCampaigns_withTurnCountGreaterThanZero() {
    String hash = createCharacterHash();
    CampaignContext ctx = campaignService.load(POSTMAN_USER_HASH, hash);
    Long campaignId = ctx.getCampaignId();

    runInTransaction(() -> {
      CampaignEntity entity = campaignRepository.findById(campaignId).orElseThrow();
      entity.setTurnCount(5);
      entity.setCurrentLocation("Elven Forest");
      entity.setNarrativeSummary("The hero began the quest.");
      campaignRepository.save(entity);
    });

    List<CampaignListItem> result = campaignService.listCampaigns(buildUserHeader());

    assertThat(result).anySatisfy(item -> {
      assertThat(item.getCharacterHash()).isEqualTo(hash);
      assertThat(item.getCharacterName()).isEqualTo("TestHero");
      assertThat(item.getRaceCode()).isEqualTo("ELF");
      assertThat(item.getCurrentTurn()).isEqualTo(5);
      assertThat(item.getCurrentLocation()).isEqualTo("Elven Forest");
      assertThat(item.getNarrativePreview()).isEqualTo("The hero began the quest.");
    });
  }

  @Test
  void listCampaigns_excludesZeroTurnCampaigns() {
    String hash = createCharacterHash();
    campaignService.load(POSTMAN_USER_HASH, hash);

    List<CampaignListItem> result = campaignService.listCampaigns(buildUserHeader());
    assertThat(result).noneMatch(item -> item.getCharacterHash().equals(hash));
  }

  @Test
  void listCampaigns_narrativePreview_fallsBackToDescriptionAndFirstGm() {
    String hash = createCharacterHash();
    CampaignContext ctx = campaignService.load(POSTMAN_USER_HASH, hash);
    Long campaignId = ctx.getCampaignId();

    runInTransaction(() -> {
      CampaignEntity campaign = campaignRepository.findById(campaignId).orElseThrow();
      campaign.setTurnCount(1);
      campaignRepository.save(campaign);

      adventureLogRepository.save(AdventureLogEntity.builder()
          .campaign(campaign).role("USER").content("explore").turnNumber(1).build());
      adventureLogRepository.save(AdventureLogEntity.builder()
          .campaign(campaign).role("GM").content("You enter the forest.").turnNumber(1).build());
    });

    List<CampaignListItem> result = campaignService.listCampaigns(buildUserHeader());

    CampaignListItem item = result.stream()
        .filter(i -> i.getCharacterHash().equals(hash)).findFirst().orElseThrow();
    assertThat(item.getNarrativePreview()).contains("A test character");
    assertThat(item.getNarrativePreview()).contains("You enter the forest.");
  }

  // ************************* getHistory tests *************************

  @Test
  void getHistory_returnsEntriesInAscOrder() {
    String hash = createCharacterHash();
    CampaignContext ctx = campaignService.load(POSTMAN_USER_HASH, hash);
    Long campaignId = ctx.getCampaignId();

    runInTransaction(() -> {
      CampaignEntity campaign = em.find(CampaignEntity.class, campaignId);
      for( int i = 1; i <= 5; i++ ){
        adventureLogRepository.save(AdventureLogEntity.builder()
            .campaign(campaign).role("USER").content("action-" + i).turnNumber(i).build());
        adventureLogRepository.save(AdventureLogEntity.builder()
            .campaign(campaign).role("GM").content("response-" + i).turnNumber(i).build());
      }
    });

    List<ChatEntry> history = campaignService.getHistory(buildUserHeader(), hash);

    assertThat(history).hasSize(5);
    assertThat(history.getFirst().turnNumber()).isEqualTo(1);
    assertThat(history.getLast().turnNumber()).isEqualTo(5);
    assertThat(history.getFirst().userMessage()).isEqualTo("action-1");
  }

  @Test
  void getHistory_emptyHistory_returnsEmptyList() {
    String hash = createCharacterHash();
    campaignService.load(POSTMAN_USER_HASH, hash);

    List<ChatEntry> history = campaignService.getHistory(buildUserHeader(), hash);

    assertThat(history).isEmpty();
  }

  @Test
  void getHistory_notOwned_throwsForbidden() {
    String hash = createCharacterHash();
    campaignService.load(POSTMAN_USER_HASH, hash);

    GameUserHeader otherUser = GameUserHeader.builder()
        .userId("00000000-0000-0000-0000-000000000001")
        .username("other")
        .build();

    assertThatThrownBy(() -> campaignService.getHistory(otherUser, hash))
        .isInstanceOf(ForbiddenException.class);
  }

  // ************************* deleteCampaign tests *************************

  @Test
  void deleteCampaign_cascadeDeletesAllChildTables_andCleansRedis() {
    String hash = createCharacterHash();
    CampaignContext ctx = campaignService.load(POSTMAN_USER_HASH, hash);
    Long campaignId = ctx.getCampaignId();

    // Populate all child tables
    runInTransaction(() -> {
      CampaignEntity campaign = em.find(CampaignEntity.class, campaignId);

      // adventure_log
      for( int i = 1; i <= 3; i++ ){
        adventureLogRepository.save(AdventureLogEntity.builder()
            .campaign(campaign).role("USER").content("action-" + i).turnNumber(i).build());
        adventureLogRepository.save(AdventureLogEntity.builder()
            .campaign(campaign).role("GM").content("response-" + i).turnNumber(i).build());
      }

      // campaign_questlog
      campaignQuestlogRepository.save(CampaignQuestlogEntity.builder()
          .campaign(campaign).turnNumber(1)
          .questActive("Find the sword").questCompleted("").build());
      campaignQuestlogRepository.save(CampaignQuestlogEntity.builder()
          .campaign(campaign).turnNumber(2)
          .questActive("Find the sword").questCompleted("Met the blacksmith").build());

      // player_inventory
      playerInventoryRepository.save(PlayerInventoryEntity.builder()
          .campaign(campaign).version(1).turnNumber(1)
          .inventory(Inventory.empty()).build());
      playerInventoryRepository.save(PlayerInventoryEntity.builder()
          .campaign(campaign).version(2).turnNumber(2)
          .inventory(Inventory.empty()).build());
    });

    // Verify child data exists before delete
    runInTransaction(() -> {
      Long logCount = (Long) em.createQuery(
              "SELECT COUNT(a) FROM AdventureLogEntity a WHERE a.campaign.characterId = :id")
          .setParameter("id", campaignId).getSingleResult();
      assertThat(logCount).isEqualTo(6);

      Long questCount = (Long) em.createQuery(
              "SELECT COUNT(q) FROM CampaignQuestlogEntity q WHERE q.campaign.characterId = :id")
          .setParameter("id", campaignId).getSingleResult();
      assertThat(questCount).isEqualTo(2);

      Long invCount = (Long) em.createQuery(
              "SELECT COUNT(i) FROM PlayerInventoryEntity i WHERE i.campaign.characterId = :id")
          .setParameter("id", campaignId).getSingleResult();
      assertThat(invCount).isEqualTo(2);
    });

    // Delete
    campaignService.deleteCampaign(buildUserHeader(), hash);

    // Verify ALL tables are clean
    runInTransaction(() -> {
      // player_character deleted
      assertThat(em.createQuery(
              "SELECT COUNT(pc) FROM PlayerCharacterEntity pc WHERE pc.characterHash = :hash", Long.class)
          .setParameter("hash", hash).getSingleResult()).isZero();

      // campaign deleted
      assertThat(campaignRepository.findById(campaignId)).isEmpty();

      // adventure_log cascade deleted
      Long logCount = (Long) em.createQuery(
              "SELECT COUNT(a) FROM AdventureLogEntity a WHERE a.campaign.characterId = :id")
          .setParameter("id", campaignId).getSingleResult();
      assertThat(logCount).isZero();

      // campaign_questlog cascade deleted
      Long questCount = (Long) em.createQuery(
              "SELECT COUNT(q) FROM CampaignQuestlogEntity q WHERE q.campaign.characterId = :id")
          .setParameter("id", campaignId).getSingleResult();
      assertThat(questCount).isZero();

      // player_inventory cascade deleted
      Long invCount = (Long) em.createQuery(
              "SELECT COUNT(i) FROM PlayerInventoryEntity i WHERE i.campaign.characterId = :id")
          .setParameter("id", campaignId).getSingleResult();
      assertThat(invCount).isZero();
    });

    // Redis cleaned
    assertThat(campaignCtxtRedis.hasKey(CTX_PREFIX + hash)).isFalse();
    assertThat(stringRedisTemplate.hasKey(TIMER_PREFIX + hash)).isFalse();
    assertThat(stringRedisTemplate.hasKey(PENDING_PREFIX + hash)).isFalse();
    assertThat(stringRedisTemplate.opsForZSet().score(ACTIVE_SESSIONS_KEY, hash)).isNull();

    createdHashes.remove(hash);
  }

  @Test
  void deleteCampaign_notOwned_throwsForbidden() {
    String hash = createCharacterHash();
    campaignService.load(POSTMAN_USER_HASH, hash);

    GameUserHeader otherUser = GameUserHeader.builder()
        .userId("00000000-0000-0000-0000-000000000001")
        .username("other")
        .build();

    assertThatThrownBy(() -> campaignService.deleteCampaign(otherUser, hash))
        .isInstanceOf(ForbiddenException.class);
  }

  // ************************* getQuestActive tests *************************

  @Test
  void getQuestActive_returnsQuestActive() {
    String hash = createCharacterHash();
    CampaignContext ctx = campaignService.load(POSTMAN_USER_HASH, hash);
    Long campaignId = ctx.getCampaignId();

    runInTransaction(() -> {
      CampaignEntity campaign = em.find(CampaignEntity.class, campaignId);
      campaignQuestlogRepository.save(CampaignQuestlogEntity.builder()
          .campaign(campaign).turnNumber(1)
          .questActive("Defeat the dragon").questCompleted("").build());
    });

    String quest = campaignService.getQuestActive(buildUserHeader(), hash);

    assertThat(quest).isEqualTo("Defeat the dragon");
  }

  @Test
  void getQuestActive_noQuestlog_returnsNull() {
    String hash = createCharacterHash();
    campaignService.load(POSTMAN_USER_HASH, hash);

    String quest = campaignService.getQuestActive(buildUserHeader(), hash);

    assertThat(quest).isNull();
  }

  @Test
  void getQuestActive_notOwned_throwsForbidden() {
    String hash = createCharacterHash();
    campaignService.load(POSTMAN_USER_HASH, hash);

    GameUserHeader otherUser = GameUserHeader.builder()
        .userId("00000000-0000-0000-0000-000000000001")
        .username("other")
        .build();

    assertThatThrownBy(() -> campaignService.getQuestActive(otherUser, hash))
        .isInstanceOf(ForbiddenException.class);
  }

  // ************************* turn_count drift (resume bug) *************************

  /**
   * Reproduces the resume bug: adventure_log has turns beyond what campaign.turn_count records.
   * This happens when Redis context is lost between periodic DB flushes.
   * The fix: load() uses MAX(adventure_log.turn_number) instead of campaign.turn_count.
   */
  @Test
  void load_turnCountDrift_usesAdventureLogMax() {
    String hash = createCharacterHash();
    CampaignContext ctx = campaignService.load(POSTMAN_USER_HASH, hash);
    Long campaignId = ctx.getCampaignId();

    // Simulate: flushed at turn 10, but player actually played to turn 14
    runInTransaction(() -> {
      CampaignEntity campaign = em.find(CampaignEntity.class, campaignId);
      campaign.setTurnCount(10);
      campaign.setLastSummaryAtTurn(0);
      campaignRepository.save(campaign);
      for( int i = 1; i <= 14; i++ ){
        adventureLogRepository.save(AdventureLogEntity.builder()
            .campaign(campaign).role("USER").content("action-" + i).turnNumber(i).build());
        adventureLogRepository.save(AdventureLogEntity.builder()
            .campaign(campaign).role("GM").content("response-" + i).turnNumber(i).build());
      }
    });

    campaignCtxtRedis.delete(CTX_PREFIX + hash);

    CampaignContext reloaded = campaignService.load(POSTMAN_USER_HASH, hash);

    assertThat(reloaded.getCurrentTurn()).isEqualTo(14);
  }

  @Test
  void load_turnCountDrift_correctsTurnsSinceLastSummary() {
    String hash = createCharacterHash();
    CampaignContext ctx = campaignService.load(POSTMAN_USER_HASH, hash);
    Long campaignId = ctx.getCampaignId();

    runInTransaction(() -> {
      CampaignEntity campaign = em.find(CampaignEntity.class, campaignId);
      campaign.setTurnCount(10);
      campaign.setLastSummaryAtTurn(5);
      campaignRepository.save(campaign);
      for( int i = 1; i <= 14; i++ ){
        adventureLogRepository.save(AdventureLogEntity.builder()
            .campaign(campaign).role("USER").content("action-" + i).turnNumber(i).build());
        adventureLogRepository.save(AdventureLogEntity.builder()
            .campaign(campaign).role("GM").content("response-" + i).turnNumber(i).build());
      }
    });

    campaignCtxtRedis.delete(CTX_PREFIX + hash);

    CampaignContext reloaded = campaignService.load(POSTMAN_USER_HASH, hash);

    // turnsSinceLastSummary = actualMaxTurn(14) - lastSummaryAtTurn(5) = 9
    assertThat(reloaded.getTurnsSinceLastSummary()).isEqualTo(9);
  }

  @Test
  void load_turnCountDrift_rebuildsRawBufferWithCorrectedTurn() {
    String hash = createCharacterHash();
    CampaignContext ctx = campaignService.load(POSTMAN_USER_HASH, hash);
    Long campaignId = ctx.getCampaignId();

    // turn_count=10 (stale), actual=14, lastSummaryAtTurn=0, recentHistorySize=10
    // Correct buffer: bufferStart=1, bufferEnd=14-10=4 → turns 1..4
    // If we used stale turn_count: bufferEnd=10-10=0 → empty buffer (WRONG)
    runInTransaction(() -> {
      CampaignEntity campaign = em.find(CampaignEntity.class, campaignId);
      campaign.setTurnCount(10);
      campaign.setLastSummaryAtTurn(0);
      campaignRepository.save(campaign);
      for( int i = 1; i <= 14; i++ ){
        adventureLogRepository.save(AdventureLogEntity.builder()
            .campaign(campaign).role("USER").content("action-" + i).turnNumber(i).build());
        adventureLogRepository.save(AdventureLogEntity.builder()
            .campaign(campaign).role("GM").content("response-" + i).turnNumber(i).build());
      }
    });

    campaignCtxtRedis.delete(CTX_PREFIX + hash);

    CampaignContext reloaded = campaignService.load(POSTMAN_USER_HASH, hash);

    assertThat(reloaded.getRawSummaryBuffer()).isNotNull().hasSize(4);
    assertThat(reloaded.getRawSummaryBuffer().getFirst().turnNumber()).isEqualTo(1);
    assertThat(reloaded.getRawSummaryBuffer().getLast().turnNumber()).isEqualTo(4);
  }

  @Test
  void load_noDrift_turnCountMatchesAdventureLog() {
    String hash = createCharacterHash();
    CampaignContext ctx = campaignService.load(POSTMAN_USER_HASH, hash);
    Long campaignId = ctx.getCampaignId();

    runInTransaction(() -> {
      CampaignEntity campaign = em.find(CampaignEntity.class, campaignId);
      campaign.setTurnCount(5);
      campaignRepository.save(campaign);
      for( int i = 1; i <= 5; i++ ){
        adventureLogRepository.save(AdventureLogEntity.builder()
            .campaign(campaign).role("USER").content("action-" + i).turnNumber(i).build());
        adventureLogRepository.save(AdventureLogEntity.builder()
            .campaign(campaign).role("GM").content("response-" + i).turnNumber(i).build());
      }
    });

    campaignCtxtRedis.delete(CTX_PREFIX + hash);

    CampaignContext reloaded = campaignService.load(POSTMAN_USER_HASH, hash);

    assertThat(reloaded.getCurrentTurn()).isEqualTo(5);
  }

  @Test
  void getHistory_duplicateTurnNumbers_returnsLatestEntriesOnly() {
    String hash = createCharacterHash();
    CampaignContext ctx = campaignService.load(POSTMAN_USER_HASH, hash);
    Long campaignId = ctx.getCampaignId();

    // Simulate two sessions writing to the same turn numbers
    runInTransaction(() -> {
      CampaignEntity campaign = em.find(CampaignEntity.class, campaignId);
      // First session: turns 1-3
      for( int i = 1; i <= 3; i++ ){
        adventureLogRepository.save(AdventureLogEntity.builder()
            .campaign(campaign).role("USER").content("session1-action-" + i).turnNumber(i).build());
        adventureLogRepository.save(AdventureLogEntity.builder()
            .campaign(campaign).role("GM").content("session1-response-" + i).turnNumber(i).build());
      }
      em.flush();
      // Second session overwrites turns 2-3
      for( int i = 2; i <= 3; i++ ){
        adventureLogRepository.save(AdventureLogEntity.builder()
            .campaign(campaign).role("USER").content("session2-action-" + i).turnNumber(i).build());
        adventureLogRepository.save(AdventureLogEntity.builder()
            .campaign(campaign).role("GM").content("session2-response-" + i).turnNumber(i).build());
      }
    });

    List<ChatEntry> history = campaignService.getHistory(buildUserHeader(), hash);

    assertThat(history).hasSize(3);
    // Turn 1: only first session entry
    assertThat(history.get(0).userMessage()).isEqualTo("session1-action-1");
    // Turns 2-3: second session entries (latest by id)
    assertThat(history.get(1).userMessage()).isEqualTo("session2-action-2");
    assertThat(history.get(2).userMessage()).isEqualTo("session2-action-3");
  }
}
