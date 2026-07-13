package com.ondgard.game.chat.service;

import com.ondgard.game.chat.TestcontainersConfiguration;
import com.ondgard.game.chat.contract.PlayerCharacterSaveRequest;
import com.ondgard.game.chat.entity.AdventureLogEntity;
import com.ondgard.game.chat.entity.CampaignEntity;
import com.ondgard.game.chat.entity.CampaignQuestlogEntity;
import com.ondgard.game.chat.entity.PlayerInventoryEntity;
import com.ondgard.game.chat.model.*;
import com.ondgard.game.chat.model.inventory.Contenitore;
import com.ondgard.game.chat.model.inventory.ExtraProperty;
import com.ondgard.game.chat.model.inventory.Inventory;
import com.ondgard.game.chat.model.inventory.InventoryItem;
import com.ondgard.game.chat.repository.*;
import com.ondgard.game.header.GameUserHeader;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests verifying that Unicode characters from multiple writing systems
 * are correctly persisted and read back through the full stack:
 * PostgreSQL (player_character, campaign_questlog, player_inventory, adventure_log)
 * and Redis (CampaignContext serialization/deserialization).
 */
@SpringBootTest
@TestPropertySource( properties = {
    "ondgard.session.flush-every-n-turns=999",
    "ondgard.session.inactivity-timeout=30s"
} )
class UnicodeIntegrationTest extends TestcontainersConfiguration {

  @Autowired private CharacterService characterService;
  @Autowired private CampaignService campaignService;
  @Autowired private RaceService raceService;
  @Autowired private CampaignRepository campaignRepository;
  @Autowired private PlayerCharacterRepository playerCharacterRepository;
  @Autowired private CampaignQuestlogRepository campaignQuestlogRepository;
  @Autowired private PlayerInventoryRepository playerInventoryRepository;
  @Autowired private AdventureLogRepository adventureLogRepository;
  @Autowired private RedisTemplate<String, CampaignContext> campaignCtxtRedis;
  @Autowired private StringRedisTemplate stringRedisTemplate;
  @Autowired private PlatformTransactionManager txManager;
  @Autowired private EntityManager em;

  private static final UUID USER_HASH = UUID.fromString("997a7552-5c1b-42c6-a0c0-094c50a0ad53");
  private static final GameUserHeader USER_HEADER = GameUserHeader.builder()
      .userId(USER_HASH.toString())
      .username("postman")
      .build();

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

  // ************************* test data *************************

  static Stream<Arguments> unicodeNames() {
    return Stream.of(
        Arguments.of("German", "Günther Müller", "Ein Ritter mit großem Schwert und Ärger im Herzen"),
        Arguments.of("Turkish", "Özgür Şahin", "İstanbul'dan gelen cesur bir savaşçı"),
        Arguments.of("Norwegian", "Bjørn Ødegård", "En modig kriger fra Ålborg med rødt hår"),
        Arguments.of("Korean", "김철수", "강력한 마법사이며 현명한 지도자"),
        Arguments.of("Japanese", "田中 太郎", "勇敢な侍で、刀の名手である"),
        Arguments.of("Cyrillic", "Иван Петров", "Могучий воин из далёких земель"),
        Arguments.of("Arabic", "عبد الله", "محارب شجاع من الصحراء"),
        Arguments.of("Mixed", "Élèna ñ Ångström", "A character with àccénts, ümlauts, and ñ")
    );
  }

  // ************************* helpers *************************

  private void runInTransaction(Runnable action) {
    new TransactionTemplate(txManager).executeWithoutResult(status -> action.run());
  }

  private String createCharacterAndTrackHash(String name, String description) {
    GameRace race = raceService.getRace("ELF");
    PlayerCharacterSaveRequest request = new PlayerCharacterSaveRequest(race, name, description);
    PlayerCharacter created = characterService.createCharacter(USER_HEADER, request);
    createdHashes.add(created.getCharacterHash());
    return created.getCharacterHash();
  }

  private void cleanupRedisKeys(String characterHash) {
    campaignCtxtRedis.delete(CTX_PREFIX + characterHash);
    stringRedisTemplate.delete(TIMER_PREFIX + characterHash);
    stringRedisTemplate.opsForZSet().remove(ACTIVE_SESSIONS_KEY, characterHash);
  }

  private void cleanupDb(String characterHash) {
    runInTransaction(() -> {
      String subquery = "(SELECT c.character_id FROM campaign c " +
          "JOIN player_character pc ON pc.id = c.character_id WHERE pc.character_hash = :hash)";
      em.createNativeQuery("DELETE FROM adventure_log WHERE campaign_id IN " + subquery)
          .setParameter("hash", characterHash).executeUpdate();
      em.createNativeQuery("DELETE FROM campaign_questlog WHERE campaign_id IN " + subquery)
          .setParameter("hash", characterHash).executeUpdate();
      em.createNativeQuery("DELETE FROM player_inventory WHERE campaign_id IN " + subquery)
          .setParameter("hash", characterHash).executeUpdate();
      em.createNativeQuery("DELETE FROM campaign_notes WHERE campaign_id IN " + subquery)
          .setParameter("hash", characterHash).executeUpdate();
      em.createNativeQuery("DELETE FROM campaign WHERE character_id IN " +
              "(SELECT id FROM player_character WHERE character_hash = :hash)")
          .setParameter("hash", characterHash).executeUpdate();
      em.createNativeQuery("DELETE FROM player_character WHERE character_hash = :hash")
          .setParameter("hash", characterHash).executeUpdate();
    });
  }

  // ************************* player_character *************************

  @ParameterizedTest( name = "{0}" )
  @MethodSource( "unicodeNames" )
  void characterService_persistsAndReadsUnicodeNameAndDescription(String lang, String name, String description) {
    String hash = createCharacterAndTrackHash(name, description);

    PlayerCharacter retrieved = characterService.getCharacter(USER_HEADER, hash);

    assertThat(retrieved.getName()).isEqualTo(name);
    assertThat(retrieved.getDescription()).isEqualTo(description);
  }

  // ************************* campaign_questlog *************************

  @ParameterizedTest( name = "{0}" )
  @MethodSource( "unicodeNames" )
  void questlog_persistsAndReadsUnicodeQuests(String lang, String name, String description) {
    String hash = createCharacterAndTrackHash(name, description);
    CampaignContext ctx = campaignService.load(USER_HASH, hash);

    runInTransaction(() -> {
      CampaignEntity campaign = campaignRepository.findById(ctx.getCampaignId()).orElseThrow();
      campaignQuestlogRepository.save(CampaignQuestlogEntity.builder()
          .campaign(campaign)
          .turnNumber(1)
          .questActive("- " + description + "\n- Trova " + name)
          .questCompleted("- Missione di " + name + " completata")
          .build());
    });

    runInTransaction(() -> {
      CampaignQuestlogEntity latest = campaignQuestlogRepository
          .findLatestByCampaignId(ctx.getCampaignId()).orElseThrow();
      assertThat(latest.getQuestActive()).contains(description);
      assertThat(latest.getQuestActive()).contains(name);
      assertThat(latest.getQuestCompleted()).contains(name);
    });
  }

  // ************************* player_inventory (JSONB) *************************

  @ParameterizedTest( name = "{0}" )
  @MethodSource( "unicodeNames" )
  void inventory_persistsUnicodeInJsonbColumn(String lang, String name, String description) {
    String hash = createCharacterAndTrackHash(name, description);
    CampaignContext ctx = campaignService.load(USER_HASH, hash);

    Inventory inventory = Inventory.builder()
        .money(100)
        .mount(Collections.emptyList())
        .contenitori(List.of(
            Contenitore.builder()
                .nome(name)
                .oggetti(List.of(
                    InventoryItem.builder()
                        .nome(description)
                        .descrizione("Oggetto di " + name)
                        .quantita(1)
                        .peso(2.5)
                        .extra(List.of(
                            ExtraProperty.builder()
                                .chiave(name)
                                .valore(description)
                                .build()))
                        .build()))
                .build()))
        .build();

    runInTransaction(() -> {
      CampaignEntity campaign = campaignRepository.findById(ctx.getCampaignId()).orElseThrow();
      playerInventoryRepository.save(PlayerInventoryEntity.builder()
          .campaign(campaign)
          .version(1)
          .turnNumber(1)
          .inventory(inventory)
          .build());
    });

    runInTransaction(() -> {
      PlayerInventoryEntity loaded = playerInventoryRepository
          .findLatestByCampaignId(ctx.getCampaignId()).orElseThrow();
      Inventory inv = loaded.getInventory();
      Contenitore contenitore = inv.getContenitori().iterator().next();

      assertThat(contenitore.getNome()).isEqualTo(name);

      InventoryItem item = contenitore.getOggetti().iterator().next();
      assertThat(item.getNome()).isEqualTo(description);
      assertThat(item.getDescrizione()).isEqualTo("Oggetto di " + name);

      ExtraProperty extra = item.getExtra().iterator().next();
      assertThat(extra.getChiave()).isEqualTo(name);
      assertThat(extra.getValore()).isEqualTo(description);
    });
  }

  // ************************* adventure_log *************************

  @ParameterizedTest( name = "{0}" )
  @MethodSource( "unicodeNames" )
  void adventureLog_persistsUnicodeContent(String lang, String name, String description) {
    String hash = createCharacterAndTrackHash(name, description);
    CampaignContext ctx = campaignService.load(USER_HASH, hash);

    String userMsg = "Voglio parlare con " + name + ". " + description;
    String gmResponse = name + " ti risponde: «" + description + "»";

    runInTransaction(() -> {
      CampaignEntity campaign = campaignRepository.findById(ctx.getCampaignId()).orElseThrow();
      adventureLogRepository.save(AdventureLogEntity.builder()
          .campaign(campaign).role("USER").content(userMsg).turnNumber(1).build());
      adventureLogRepository.save(AdventureLogEntity.builder()
          .campaign(campaign).role("GM").content(gmResponse).turnNumber(1).build());
    });

    List<ChatEntry> history = campaignService.getHistory(USER_HEADER, hash);

    assertThat(history).hasSize(1);
    assertThat(history.getFirst().userMessage()).isEqualTo(userMsg);
    assertThat(history.getFirst().gmResponse()).isEqualTo(gmResponse);
  }

  // ************************* Redis CampaignContext *************************

  @ParameterizedTest( name = "{0}" )
  @MethodSource( "unicodeNames" )
  void redis_serializesAndDeserializesUnicodeContext(String lang, String name, String description) {
    String hash = createCharacterAndTrackHash(name, description);
    CampaignContext ctx = campaignService.load(USER_HASH, hash);

    ctx.setNarrativeSummary("Riassunto: " + description);
    ctx.setScene(GameScene.builder()
        .currentLocation(name + " - città")
        .gameDate("15 " + name)
        .gameTime("tramonto")
        .meteo(description)
        .temperature("20°C")
        .build());
    ctx.setQuestLog(new CampaignQuestLog(1,
        "- Quest: " + description,
        "- Completata: missione di " + name));
    ctx.setInventory(Inventory.builder()
        .money(50)
        .mount(Collections.emptyList())
        .contenitori(List.of(Contenitore.builder()
            .nome(name)
            .oggetti(List.of(InventoryItem.builder()
                .nome(description)
                .descrizione("Artefatto di " + name)
                .quantita(1)
                .peso(1.0)
                .build()))
            .build()))
        .build());
    ctx.setRecentHistory(List.of(
        new ChatEntry(1, "Messaggio: " + name, "Risposta: " + description)));

    campaignService.saveContextOnly(ctx, hash);

    // Clear and reload from Redis
    CampaignContext loaded = campaignCtxtRedis.opsForValue().get(CTX_PREFIX + hash);

    assertThat(loaded).isNotNull();
    assertThat(loaded.getNarrativeSummary()).isEqualTo("Riassunto: " + description);
    assertThat(loaded.getScene().getCurrentLocation()).isEqualTo(name + " - città");
    assertThat(loaded.getScene().getMeteo()).isEqualTo(description);
    assertThat(loaded.getQuestLog().questActive()).contains(description);
    assertThat(loaded.getQuestLog().questCompleted()).contains(name);

    Contenitore contenitore = loaded.getInventory().getContenitori().iterator().next();
    assertThat(contenitore.getNome()).isEqualTo(name);
    assertThat(contenitore.getOggetti().iterator().next().getNome()).isEqualTo(description);

    assertThat(loaded.getRecentHistory()).hasSize(1);
    assertThat(loaded.getRecentHistory().getFirst().userMessage()).isEqualTo("Messaggio: " + name);
    assertThat(loaded.getRecentHistory().getFirst().gmResponse()).isEqualTo("Risposta: " + description);
  }

  // ************************* full round-trip: Redis → DB → Redis *************************

  @Test
  void fullRoundTrip_unicodeDataSurvivesRedisFlushAndDbReload() {
    String hash = createCharacterAndTrackHash("Ярослав Østberg 김태준", "Héros légendaire: 伝説の英雄 — Бессмертный");
    CampaignContext ctx = campaignService.load(USER_HASH, hash);

    String narrativeSummary = "Ярослав ha attraversato le terre di 김태준. "
        + "Østberg è stata raggiunta. 伝説の英雄 si è rivelato.";
    String questActive = "- Trovare la spada di Ярослав\n- Parlare con 김태준\n- Через горы к Østberg";
    String questCompleted = "- Бессмертный sconfitto\n- 伝説の英雄 liberato";
    String location = "Замок Østberg — 城 di 김태준";

    // Populate context with multi-script data
    ctx.setNarrativeSummary(narrativeSummary);
    ctx.setCurrentTurn(3);
    ctx.setScene(GameScene.builder()
        .currentLocation(location)
        .gameDate("15 Марта")
        .gameTime("日没")
        .meteo("Fırtına")
        .temperature("-5°C (çok soğuk)")
        .build());
    ctx.setQuestLog(new CampaignQuestLog(3, questActive, questCompleted));
    ctx.setInventory(Inventory.builder()
        .money(250)
        .mount(Collections.emptyList())
        .contenitori(List.of(
            Contenitore.builder()
                .nome("Рюкзак")
                .oggetti(List.of(
                    InventoryItem.builder()
                        .nome("刀 (카타나)")
                        .descrizione("Katana magica di Ярослав — forgiata a Østberg")
                        .quantita(1).peso(3.5)
                        .extra(List.of(
                            ExtraProperty.builder().chiave("Dövüş gücü").valore("çok yüksek").build(),
                            ExtraProperty.builder().chiave("魔力").valore("伝説級").build()))
                        .build()))
                .build()))
        .build());
    ctx.setRecentHistory(List.of(
        new ChatEntry(1, "Parlo con Ярослав Østberg", "Ярослав ti risponde: «Benvenuto, 김태준»"),
        new ChatEntry(2, "김태준 は剣を抜く", "Il GM narra: 伝説の英雄 appare davanti a te"),
        new ChatEntry(3, "Attraverso le montagne verso Østberg", "Arrivi al Замок. Fırtına imperversa.")));

    campaignService.saveContextOnly(ctx, hash);

    // Persist questlog and inventory to DB
    runInTransaction(() -> {
      CampaignEntity campaign = campaignRepository.findById(ctx.getCampaignId()).orElseThrow();
      campaignQuestlogRepository.save(CampaignQuestlogEntity.builder()
          .campaign(campaign).turnNumber(3)
          .questActive(questActive).questCompleted(questCompleted).build());
      playerInventoryRepository.save(PlayerInventoryEntity.builder()
          .campaign(campaign).version(1).turnNumber(3)
          .inventory(ctx.getInventory()).build());
      for( ChatEntry entry : ctx.getRecentHistory() ){
        adventureLogRepository.save(AdventureLogEntity.builder()
            .campaign(campaign).role("USER").content(entry.userMessage()).turnNumber(entry.turnNumber()).build());
        adventureLogRepository.save(AdventureLogEntity.builder()
            .campaign(campaign).role("GM").content(entry.gmResponse()).turnNumber(entry.turnNumber()).build());
      }
      campaign.setNarrativeSummary(narrativeSummary);
      campaign.setCurrentLocation(location);
      campaign.setGameDate("15 Марта");
      campaign.setGameTime("日没");
      campaign.setMeteo("Fırtına");
      campaign.setTemperature("-5°C (çok soğuk)");
      campaign.setTurnCount(3);
      campaignRepository.save(campaign);
    });

    // Evict Redis cache to force DB reload
    campaignCtxtRedis.delete(CTX_PREFIX + hash);
    stringRedisTemplate.delete(TIMER_PREFIX + hash);
    stringRedisTemplate.opsForZSet().remove(ACTIVE_SESSIONS_KEY, hash);

    // Reload from DB through the service
    CampaignContext reloaded = campaignService.load(USER_HASH, hash);

    // Verify narrative
    assertThat(reloaded.getNarrativeSummary()).isEqualTo(narrativeSummary);
    assertThat(reloaded.getScene().getCurrentLocation()).isEqualTo(location);
    assertThat(reloaded.getScene().getMeteo()).isEqualTo("Fırtına");
    assertThat(reloaded.getScene().getTemperature()).isEqualTo("-5°C (çok soğuk)");
    assertThat(reloaded.getScene().getGameDate()).isEqualTo("15 Марта");
    assertThat(reloaded.getScene().getGameTime()).isEqualTo("日没");

    // Verify questlog loaded from DB
    assertThat(reloaded.getQuestLog().questActive()).isEqualTo(questActive);
    assertThat(reloaded.getQuestLog().questCompleted()).isEqualTo(questCompleted);

    // Verify inventory loaded from DB (JSONB round-trip)
    Contenitore contenitore = reloaded.getInventory().getContenitori().iterator().next();
    assertThat(contenitore.getNome()).isEqualTo("Рюкзак");
    InventoryItem item = contenitore.getOggetti().iterator().next();
    assertThat(item.getNome()).isEqualTo("刀 (카타나)");
    assertThat(item.getDescrizione()).contains("Ярослав").contains("Østberg");

    // Verify history loaded from DB
    assertThat(reloaded.getRecentHistory()).hasSize(3);
    assertThat(reloaded.getRecentHistory().getFirst().userMessage()).contains("Ярослав");
    assertThat(reloaded.getRecentHistory().getLast().gmResponse()).contains("Fırtına");

    // Verify the reloaded context is also cached back in Redis
    CampaignContext reCached = campaignCtxtRedis.opsForValue().get(CTX_PREFIX + hash);
    assertThat(reCached).isNotNull();
    assertThat(reCached.getNarrativeSummary()).isEqualTo(narrativeSummary);
  }
}
