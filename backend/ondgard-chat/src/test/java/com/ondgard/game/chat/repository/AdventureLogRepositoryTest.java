package com.ondgard.game.chat.repository;

import com.ondgard.game.chat.TestcontainersConfiguration;
import com.ondgard.game.chat.entity.AdventureLogEntity;
import com.ondgard.game.chat.entity.CampaignEntity;
import com.ondgard.game.chat.entity.PlayerCharacterEntity;
import com.ondgard.game.chat.repository.projection.AdventureLogTurnProjection;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class AdventureLogRepositoryTest extends TestcontainersConfiguration {

  @Autowired private AdventureLogRepository adventureLogRepository;
  @Autowired private CampaignRepository campaignRepository;
  @Autowired private PlayerCharacterRepository playerCharacterRepository;
  @Autowired private EntityManager em;

  private static final UUID POSTMAN_USER_HASH = UUID.fromString("997a7552-5c1b-42c6-a0c0-094c50a0ad53");

  private CampaignEntity campaign;

  @BeforeEach
  void setUp() {
    String hash = "test-char-" + UUID.randomUUID().toString().substring(0, 20);
    playerCharacterRepository.insertCharacter(POSTMAN_USER_HASH, "ELF", hash, "TestHero", "A test character");
    em.flush();
    em.clear();

    PlayerCharacterEntity character = em.createQuery(
            "SELECT pc FROM PlayerCharacterEntity pc WHERE pc.characterHash = :hash", PlayerCharacterEntity.class)
        .setParameter("hash", hash)
        .getSingleResult();

    campaign = CampaignEntity.builder().character(character).build();
    campaignRepository.save(campaign);
    em.flush();
    em.clear();

    campaign = campaignRepository.findById(character.getId()).orElseThrow();
  }

  private void insertTurn(CampaignEntity campaign, int turnNumber, String userMsg, String gmMsg) {
    adventureLogRepository.save(AdventureLogEntity.builder()
        .campaign(campaign).role("USER").content(userMsg).turnNumber(turnNumber).build());
    adventureLogRepository.save(AdventureLogEntity.builder()
        .campaign(campaign).role("GM").content(gmMsg).turnNumber(turnNumber).build());
  }

  @Test
  void findRecentTurns_returnsPairedProjections() {
    for( int i = 1; i <= 5; i++ ){
      insertTurn(campaign, i, "action-" + i, "response-" + i);
    }
    em.flush();
    em.clear();

    List<AdventureLogTurnProjection> turns = adventureLogRepository.findRecentTurns(
        campaign.getCharacterId(), 10);

    assertThat(turns).hasSize(5);
    assertThat(turns.getFirst().turnNumber()).isEqualTo(5);
    assertThat(turns.getLast().turnNumber()).isEqualTo(1);

    AdventureLogTurnProjection turn3 = turns.get(2);
    assertThat(turn3.turnNumber()).isEqualTo(3);
    assertThat(turn3.userMessage()).isEqualTo("action-3");
    assertThat(turn3.gmResponse()).isEqualTo("response-3");
    assertThat(turn3.created()).isNotNull();
  }

  @Test
  void findRecentTurns_paginationLimitsResults() {
    for( int i = 1; i <= 5; i++ ){
      insertTurn(campaign, i, "action-" + i, "response-" + i);
    }
    em.flush();
    em.clear();

    List<AdventureLogTurnProjection> turns = adventureLogRepository.findRecentTurns(
        campaign.getCharacterId(), 3);

    assertThat(turns).hasSize(3);
    assertThat(turns.get(0).turnNumber()).isEqualTo(5);
    assertThat(turns.get(1).turnNumber()).isEqualTo(4);
    assertThat(turns.get(2).turnNumber()).isEqualTo(3);
  }

  @Test
  void findRecentTurns_fewerTurnsThanRequested() {
    insertTurn(campaign, 1, "action-1", "response-1");
    insertTurn(campaign, 2, "action-2", "response-2");
    em.flush();
    em.clear();

    List<AdventureLogTurnProjection> turns = adventureLogRepository.findRecentTurns(
        campaign.getCharacterId(), 5);

    assertThat(turns).hasSize(2);
  }

  @Test
  void findRecentTurns_emptyCampaign_returnsEmptyList() {
    List<AdventureLogTurnProjection> turns = adventureLogRepository.findRecentTurns(
        campaign.getCharacterId(), 10);

    assertThat(turns).isEmpty();
  }

  // ************************* findTurnRange *************************

  @Test
  void findTurnRange_returnsCorrectRangeInAscOrder() {
    for( int i = 1; i <= 10; i++ ){
      insertTurn(campaign, i, "action-" + i, "response-" + i);
    }
    em.flush();
    em.clear();

    List<AdventureLogTurnProjection> turns = adventureLogRepository.findTurnRange(
        campaign.getCharacterId(), 3, 7);

    assertThat(turns).hasSize(5);
    assertThat(turns.getFirst().turnNumber()).isEqualTo(3);
    assertThat(turns.getLast().turnNumber()).isEqualTo(7);

    AdventureLogTurnProjection turn5 = turns.get(2);
    assertThat(turn5.turnNumber()).isEqualTo(5);
    assertThat(turn5.userMessage()).isEqualTo("action-5");
    assertThat(turn5.gmResponse()).isEqualTo("response-5");
  }

  @Test
  void findTurnRange_emptyRange_returnsEmptyList() {
    for( int i = 1; i <= 3; i++ ){
      insertTurn(campaign, i, "action-" + i, "response-" + i);
    }
    em.flush();
    em.clear();

    List<AdventureLogTurnProjection> turns = adventureLogRepository.findTurnRange(
        campaign.getCharacterId(), 10, 20);

    assertThat(turns).isEmpty();
  }

  @Test
  void findTurnRange_singleTurn_returnsOneTurn() {
    for( int i = 1; i <= 5; i++ ){
      insertTurn(campaign, i, "action-" + i, "response-" + i);
    }
    em.flush();
    em.clear();

    List<AdventureLogTurnProjection> turns = adventureLogRepository.findTurnRange(
        campaign.getCharacterId(), 3, 3);

    assertThat(turns).hasSize(1);
    assertThat(turns.getFirst().turnNumber()).isEqualTo(3);
  }

  // ************************* findRecentTurns — duplicate turn numbers *************************

  @Test
  void findRecentTurns_duplicateTurnNumbers_returnsLatestEntryPerTurn() {
    // Simulate the bug: two sessions wrote different content for turn 3
    insertTurn(campaign, 1, "action-1", "response-1");
    insertTurn(campaign, 2, "action-2", "response-2");
    insertTurn(campaign, 3, "first-session-action-3", "first-session-response-3");
    em.flush();

    // Second session overwrites turn 3 with new content (higher ids)
    insertTurn(campaign, 3, "second-session-action-3", "second-session-response-3");
    em.flush();
    em.clear();

    List<AdventureLogTurnProjection> turns = adventureLogRepository.findRecentTurns(
        campaign.getCharacterId(), 10);

    assertThat(turns).hasSize(3);
    AdventureLogTurnProjection turn3 = turns.getFirst(); // DESC order, turn 3 is first
    assertThat(turn3.turnNumber()).isEqualTo(3);
    assertThat(turn3.userMessage()).isEqualTo("second-session-action-3");
    assertThat(turn3.gmResponse()).isEqualTo("second-session-response-3");
  }

  @Test
  void findRecentTurns_multipleDuplicates_noCrossJoinExplosion() {
    // 2 USER + 2 GM for same turn → old JPQL would produce 4 rows (2×2 cartesian)
    insertTurn(campaign, 1, "action-1", "response-1");
    insertTurn(campaign, 2, "session1-action-2", "session1-response-2");
    insertTurn(campaign, 2, "session2-action-2", "session2-response-2");
    em.flush();
    em.clear();

    List<AdventureLogTurnProjection> turns = adventureLogRepository.findRecentTurns(
        campaign.getCharacterId(), 10);

    assertThat(turns).hasSize(2);
    assertThat(turns.get(0).turnNumber()).isEqualTo(2);
    assertThat(turns.get(1).turnNumber()).isEqualTo(1);
  }

  // ************************* findTurnRange — duplicate turn numbers *************************

  @Test
  void findTurnRange_duplicateTurnNumbers_returnsLatestEntryPerTurn() {
    for( int i = 1; i <= 5; i++ ){
      insertTurn(campaign, i, "session1-action-" + i, "session1-response-" + i);
    }
    em.flush();

    // Second session overwrites turns 3-4
    insertTurn(campaign, 3, "session2-action-3", "session2-response-3");
    insertTurn(campaign, 4, "session2-action-4", "session2-response-4");
    em.flush();
    em.clear();

    List<AdventureLogTurnProjection> turns = adventureLogRepository.findTurnRange(
        campaign.getCharacterId(), 2, 4);

    assertThat(turns).hasSize(3);
    assertThat(turns.get(0).userMessage()).isEqualTo("session1-action-2");
    assertThat(turns.get(1).userMessage()).isEqualTo("session2-action-3");
    assertThat(turns.get(2).userMessage()).isEqualTo("session2-action-4");
  }

  // ************************* findMaxTurnNumber *************************

  @Test
  void findMaxTurnNumber_returnHighestTurn() {
    for( int i = 1; i <= 7; i++ ){
      insertTurn(campaign, i, "action-" + i, "response-" + i);
    }
    em.flush();
    em.clear();

    int max = adventureLogRepository.findMaxTurnNumber(campaign.getCharacterId());

    assertThat(max).isEqualTo(7);
  }

  @Test
  void findMaxTurnNumber_emptyCampaign_returnsZero() {
    int max = adventureLogRepository.findMaxTurnNumber(campaign.getCharacterId());

    assertThat(max).isZero();
  }

  // ************************* findFirstGmResponse *************************

  @Test
  void findFirstGmResponse_returnsFirstTurnGmContent() {
    for( int i = 1; i <= 3; i++ ){
      insertTurn(campaign, i, "action-" + i, "gm-response-" + i);
    }
    em.flush();
    em.clear();

    Optional<String> result = adventureLogRepository.findFirstGmResponse(campaign.getCharacterId());

    assertThat(result).isPresent();
    assertThat(result.get()).isEqualTo("gm-response-1");
  }

  @Test
  void findFirstGmResponse_noEntries_returnsEmpty() {
    Optional<String> result = adventureLogRepository.findFirstGmResponse(campaign.getCharacterId());

    assertThat(result).isEmpty();
  }

  @Test
  void findFirstGmResponse_onlyUserEntries_returnsEmpty() {
    adventureLogRepository.save(AdventureLogEntity.builder()
        .campaign(campaign).role("USER").content("action-1").turnNumber(1).build());
    em.flush();
    em.clear();

    Optional<String> result = adventureLogRepository.findFirstGmResponse(campaign.getCharacterId());

    assertThat(result).isEmpty();
  }
}
