package com.ondgard.game.chat.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ondgard.game.chat.TestcontainersConfiguration;
import com.ondgard.game.chat.contract.PlayerCharacterSaveRequest;
import com.ondgard.game.chat.entity.AdventureLogEntity;
import com.ondgard.game.chat.entity.CampaignEntity;
import com.ondgard.game.chat.entity.CampaignQuestlogEntity;
import com.ondgard.game.chat.model.CampaignContext;
import com.ondgard.game.chat.model.GameRace;
import com.ondgard.game.chat.model.GameScene;
import com.ondgard.game.chat.model.PlayerCharacter;
import com.ondgard.game.chat.repository.*;
import com.ondgard.game.chat.service.CampaignService;
import com.ondgard.game.chat.service.CharacterService;
import com.ondgard.game.chat.service.RaceService;
import com.ondgard.game.header.GameUserHeader;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class CampaignControllerTest extends TestcontainersConfiguration {

  @Autowired private MockMvc mockMvc;
  @Autowired private CharacterService characterService;
  @Autowired private CampaignService campaignService;
  @Autowired private RaceService raceService;
  @Autowired private CampaignRepository campaignRepository;
  @Autowired private AdventureLogRepository adventureLogRepository;
  @Autowired private CampaignQuestlogRepository campaignQuestlogRepository;
  @Autowired private CampaignNotesRepository campaignNotesRepository;
  @Autowired private PlayerCharacterRepository playerCharacterRepository;
  @Autowired private RedisTemplate<String, CampaignContext> campaignCtxtRedis;
  @Autowired private StringRedisTemplate stringRedisTemplate;
  @Autowired private PlatformTransactionManager txManager;
  @Autowired private EntityManager em;

  private final ObjectMapper objectMapper = new ObjectMapper();

  private static final String POSTMAN_USER_HASH = "997a7552-5c1b-42c6-a0c0-094c50a0ad53";
  private static final String POSTMAN_USERNAME = "postman";
  private static final String CTX_PREFIX = "ondgard:ctx:";
  private static final String TIMER_PREFIX = "ondgard:session-timer:";
  private static final String ACTIVE_SESSIONS_KEY = "ondgard:active-sessions";

  private final List<String> createdHashes = new ArrayList<>();

  private String buildUserHeader() throws Exception {
    GameUserHeader header = GameUserHeader.builder()
        .userId(POSTMAN_USER_HASH)
        .username(POSTMAN_USERNAME)
        .build();
    return objectMapper.writeValueAsString(header);
  }

  private String buildUserHeader(String userId) throws Exception {
    GameUserHeader header = GameUserHeader.builder()
        .userId(userId)
        .username("other-user")
        .build();
    return objectMapper.writeValueAsString(header);
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

  private void cleanupDb(String characterHash) {
    new TransactionTemplate(txManager).executeWithoutResult(status -> {
      em.createNativeQuery(
              "DELETE FROM adventure_log WHERE campaign_id IN " +
                  "(SELECT c.character_id FROM campaign c JOIN player_character pc ON pc.id = c.character_id WHERE pc.character_hash = :hash)")
          .setParameter("hash", characterHash)
          .executeUpdate();
      em.createNativeQuery(
              "DELETE FROM campaign_notes WHERE campaign_id IN " +
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

  private String createCharacterWithCampaign() {
    GameUserHeader header = GameUserHeader.builder()
        .userId(POSTMAN_USER_HASH)
        .username(POSTMAN_USERNAME)
        .build();
    GameRace elf = raceService.getRace("ELF");
    PlayerCharacter character = characterService.createCharacter(header,
        new PlayerCharacterSaveRequest(elf, "TestHero", "A test character"));
    String characterHash = character.getCharacterHash();

    CampaignContext ctx = campaignService.load(UUID.fromString(POSTMAN_USER_HASH), characterHash);
    ctx.setScene(GameScene.builder().currentLocation("Test Location").build());
    campaignService.save(ctx, characterHash);

    createdHashes.add(characterHash);
    return characterHash;
  }

  @Test
  void endSession_validOwner_returns200AndFlushes() throws Exception {
    String characterHash = createCharacterWithCampaign();

    mockMvc.perform(post("/api/campaign/session/end")
            .param("characterHash", characterHash)
            .header(GameUserHeader.HEADER_NAME, buildUserHeader()))
        .andExpect(status().isOk());

    assertThat(campaignCtxtRedis.hasKey(CTX_PREFIX + characterHash)).isFalse();
    assertThat(stringRedisTemplate.hasKey(TIMER_PREFIX + characterHash)).isFalse();
    assertThat(stringRedisTemplate.opsForZSet().score(ACTIVE_SESSIONS_KEY, characterHash)).isNull();

    CampaignEntity entity = campaignRepository.findByCharacterHashAndUserHash(
        UUID.fromString(POSTMAN_USER_HASH), characterHash).orElseThrow();
    assertThat(entity.getCurrentLocation()).isEqualTo("Test Location");
  }

  @Test
  void endSession_characterNotOwned_returns403() throws Exception {
    String characterHash = createCharacterWithCampaign();

    mockMvc.perform(post("/api/campaign/session/end")
            .param("characterHash", characterHash)
            .header(GameUserHeader.HEADER_NAME, buildUserHeader("00000000-0000-0000-0000-000000000001")))
        .andExpect(status().isForbidden());
  }

  @Test
  void endSession_nonexistentCharacter_returns403() throws Exception {
    mockMvc.perform(post("/api/campaign/session/end")
            .param("characterHash", "nonexistent-hash-12345")
            .header(GameUserHeader.HEADER_NAME, buildUserHeader()))
        .andExpect(status().isForbidden());
  }

  @Test
  void endSession_missingParam_returns400() throws Exception {
    mockMvc.perform(post("/api/campaign/session/end")
            .header(GameUserHeader.HEADER_NAME, buildUserHeader()))
        .andExpect(status().isBadRequest());
  }

  // ************************* listCampaigns *************************

  @Test
  void listCampaigns_returns200_withCampaignsHavingTurns() throws Exception {
    String characterHash = createCharacterWithCampaign();
    CampaignEntity entity = campaignRepository.findByCharacterHashAndUserHash(
        UUID.fromString(POSTMAN_USER_HASH), characterHash).orElseThrow();
    entity.setTurnCount(3);
    entity.setCurrentLocation("Dark Forest");
    campaignRepository.save(entity);

    mockMvc.perform(get("/api/campaign/list")
            .header(GameUserHeader.HEADER_NAME, buildUserHeader()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", not(empty())))
        .andExpect(jsonPath("$[?(@.characterHash=='" + characterHash + "')].characterName",
            hasItem("TestHero")))
        .andExpect(jsonPath("$[?(@.characterHash=='" + characterHash + "')].raceCode",
            hasItem("ELF")))
        .andExpect(jsonPath("$[?(@.characterHash=='" + characterHash + "')].currentTurn",
            hasItem(3)));
  }

  @Test
  void listCampaigns_excludesZeroTurnCampaigns() throws Exception {
    String characterHash = createCharacterWithCampaign(); // turnCount stays 0

    mockMvc.perform(get("/api/campaign/list")
            .header(GameUserHeader.HEADER_NAME, buildUserHeader()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.characterHash=='" + characterHash + "')]", empty()));
  }

  // ************************* getHistory *************************

  @Test
  void getHistory_returns200_withHistoryInAscOrder() throws Exception {
    String characterHash = createCharacterWithCampaign();
    CampaignEntity campaign = campaignRepository.findByCharacterHashAndUserHash(
        UUID.fromString(POSTMAN_USER_HASH), characterHash).orElseThrow();

    for( int i = 1; i <= 3; i++ ){
      adventureLogRepository.save(AdventureLogEntity.builder()
          .campaign(campaign).role("USER").content("action-" + i).turnNumber(i).build());
      adventureLogRepository.save(AdventureLogEntity.builder()
          .campaign(campaign).role("GM").content("response-" + i).turnNumber(i).build());
    }

    mockMvc.perform(get("/api/campaign/history")
            .param("characterHash", characterHash)
            .header(GameUserHeader.HEADER_NAME, buildUserHeader()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(3)))
        .andExpect(jsonPath("$[0].turnNumber").value(1))
        .andExpect(jsonPath("$[0].userMessage").value("action-1"))
        .andExpect(jsonPath("$[0].gmResponse").value("response-1"))
        .andExpect(jsonPath("$[2].turnNumber").value(3));
  }

  @Test
  void getHistory_emptyHistory_returnsEmptyList() throws Exception {
    String characterHash = createCharacterWithCampaign();

    mockMvc.perform(get("/api/campaign/history")
            .param("characterHash", characterHash)
            .header(GameUserHeader.HEADER_NAME, buildUserHeader()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", empty()));
  }

  @Test
  void getHistory_notOwned_returns403() throws Exception {
    String characterHash = createCharacterWithCampaign();

    mockMvc.perform(get("/api/campaign/history")
            .param("characterHash", characterHash)
            .header(GameUserHeader.HEADER_NAME, buildUserHeader("00000000-0000-0000-0000-000000000001")))
        .andExpect(status().isForbidden());
  }

  // ************************* getQuestActive *************************

  @Test
  void getQuestActive_returns200_withQuest() throws Exception {
    String characterHash = createCharacterWithCampaign();
    CampaignEntity campaign = campaignRepository.findByCharacterHashAndUserHash(
        UUID.fromString(POSTMAN_USER_HASH), characterHash).orElseThrow();

    campaignQuestlogRepository.save(CampaignQuestlogEntity.builder()
        .campaign(campaign).turnNumber(1)
        .questActive("Find the ancient sword")
        .questCompleted("")
        .build());

    mockMvc.perform(get("/api/campaign/quest-active")
            .param("characterHash", characterHash)
            .header(GameUserHeader.HEADER_NAME, buildUserHeader()))
        .andExpect(status().isOk())
        .andExpect(content().string("Find the ancient sword"));
  }

  @Test
  void getQuestActive_noQuestlog_returns200_empty() throws Exception {
    String characterHash = createCharacterWithCampaign();

    mockMvc.perform(get("/api/campaign/quest-active")
            .param("characterHash", characterHash)
            .header(GameUserHeader.HEADER_NAME, buildUserHeader()))
        .andExpect(status().isOk());
  }

  @Test
  void getQuestActive_notOwned_returns403() throws Exception {
    String characterHash = createCharacterWithCampaign();

    mockMvc.perform(get("/api/campaign/quest-active")
            .param("characterHash", characterHash)
            .header(GameUserHeader.HEADER_NAME, buildUserHeader("00000000-0000-0000-0000-000000000001")))
        .andExpect(status().isForbidden());
  }

  // ************************* deleteCampaign *************************

  @Test
  void deleteCampaign_validOwner_returns200_deletesFromDb() throws Exception {
    String characterHash = createCharacterWithCampaign();

    mockMvc.perform(delete("/api/campaign/" + characterHash)
            .header(GameUserHeader.HEADER_NAME, buildUserHeader()))
        .andExpect(status().isOk());

    assertThat(campaignRepository.findByCharacterHashAndUserHash(
        UUID.fromString(POSTMAN_USER_HASH), characterHash)).isEmpty();
    assertThat(campaignCtxtRedis.hasKey(CTX_PREFIX + characterHash)).isFalse();
    assertThat(stringRedisTemplate.hasKey(TIMER_PREFIX + characterHash)).isFalse();
    assertThat(stringRedisTemplate.opsForZSet().score(ACTIVE_SESSIONS_KEY, characterHash)).isNull();

    createdHashes.remove(characterHash);
  }

  @Test
  void deleteCampaign_notOwned_returns403() throws Exception {
    String characterHash = createCharacterWithCampaign();

    mockMvc.perform(delete("/api/campaign/" + characterHash)
            .header(GameUserHeader.HEADER_NAME, buildUserHeader("00000000-0000-0000-0000-000000000001")))
        .andExpect(status().isForbidden());
  }

  @Test
  void deleteCampaign_nonexistent_returns403() throws Exception {
    mockMvc.perform(delete("/api/campaign/nonexistent-hash-12345")
            .header(GameUserHeader.HEADER_NAME, buildUserHeader()))
        .andExpect(status().isForbidden());
  }

  // ************************* playerNotes *************************

  @Test
  void getPlayerNotes_newCampaign_returns200_emptyString() throws Exception {
    String characterHash = createCharacterWithCampaign();

    mockMvc.perform(get("/api/campaign/playerNotes")
            .param("characterHash", characterHash)
            .header(GameUserHeader.HEADER_NAME, buildUserHeader()))
        .andExpect(status().isOk())
        .andExpect(content().string(""));
  }

  @Test
  void getPlayerNotes_withContent_returns200() throws Exception {
    String characterHash = createCharacterWithCampaign();
    CampaignEntity campaign = campaignRepository.findByCharacterHashAndUserHash(
        UUID.fromString(POSTMAN_USER_HASH), characterHash).orElseThrow();

    new TransactionTemplate(txManager).executeWithoutResult(status ->
        campaignNotesRepository.updateContent(campaign.getCharacterId(), "My adventure notes"));

    mockMvc.perform(get("/api/campaign/playerNotes")
            .param("characterHash", characterHash)
            .header(GameUserHeader.HEADER_NAME, buildUserHeader()))
        .andExpect(status().isOk())
        .andExpect(content().string("My adventure notes"));
  }

  @Test
  void getPlayerNotes_notOwned_returns204() throws Exception {
    String characterHash = createCharacterWithCampaign();

    mockMvc.perform(get("/api/campaign/playerNotes")
            .param("characterHash", characterHash)
            .header(GameUserHeader.HEADER_NAME, buildUserHeader("00000000-0000-0000-0000-000000000001")))
        .andExpect(status().isNoContent());
  }

  @Test
  void updatePlayerNotes_returns200_andPersists() throws Exception {
    String characterHash = createCharacterWithCampaign();

    String body = objectMapper.writeValueAsString(
        new java.util.LinkedHashMap<>() {{
          put("characterHash", characterHash);
          put("newNotesSnapshot", "First notes entry");
        }});

    mockMvc.perform(put("/api/campaign/playerNotes")
            .contentType("application/json")
            .content(body)
            .header(GameUserHeader.HEADER_NAME, buildUserHeader()))
        .andExpect(status().isOk());

    mockMvc.perform(get("/api/campaign/playerNotes")
            .param("characterHash", characterHash)
            .header(GameUserHeader.HEADER_NAME, buildUserHeader()))
        .andExpect(status().isOk())
        .andExpect(content().string("First notes entry"));
  }

  @Test
  void updatePlayerNotes_overwritesPrevious() throws Exception {
    String characterHash = createCharacterWithCampaign();

    String body1 = objectMapper.writeValueAsString(
        new java.util.LinkedHashMap<>() {{
          put("characterHash", characterHash);
          put("newNotesSnapshot", "Old notes");
        }});
    String body2 = objectMapper.writeValueAsString(
        new java.util.LinkedHashMap<>() {{
          put("characterHash", characterHash);
          put("newNotesSnapshot", "New notes");
        }});

    mockMvc.perform(put("/api/campaign/playerNotes")
            .contentType("application/json")
            .content(body1)
            .header(GameUserHeader.HEADER_NAME, buildUserHeader()))
        .andExpect(status().isOk());

    mockMvc.perform(put("/api/campaign/playerNotes")
            .contentType("application/json")
            .content(body2)
            .header(GameUserHeader.HEADER_NAME, buildUserHeader()))
        .andExpect(status().isOk());

    mockMvc.perform(get("/api/campaign/playerNotes")
            .param("characterHash", characterHash)
            .header(GameUserHeader.HEADER_NAME, buildUserHeader()))
        .andExpect(status().isOk())
        .andExpect(content().string("New notes"));
  }

  @Test
  void updatePlayerNotes_notOwned_returns204() throws Exception {
    String characterHash = createCharacterWithCampaign();

    String body = objectMapper.writeValueAsString(
        new java.util.LinkedHashMap<>() {{
          put("characterHash", characterHash);
          put("newNotesSnapshot", "Hacked notes");
        }});

    mockMvc.perform(put("/api/campaign/playerNotes")
            .contentType("application/json")
            .content(body)
            .header(GameUserHeader.HEADER_NAME, buildUserHeader("00000000-0000-0000-0000-000000000001")))
        .andExpect(status().isNoContent());
  }
}
