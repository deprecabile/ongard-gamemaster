package com.ondgard.game.chat.repository;

import com.ondgard.game.chat.TestcontainersConfiguration;
import com.ondgard.game.chat.entity.AdvisorLogEntity;
import com.ondgard.game.chat.entity.CampaignEntity;
import com.ondgard.game.chat.entity.PlayerCharacterEntity;
import com.ondgard.game.chat.repository.projection.AdvisorLogProjection;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class AdvisorLogRepositoryTest extends TestcontainersConfiguration {

  @Autowired private AdvisorLogRepository advisorLogRepository;
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

  private void insertExchange(CampaignEntity campaign, int turnNumber,
                              String question, String answer) {
    advisorLogRepository.save(AdvisorLogEntity.builder()
        .campaign(campaign)
        .turnNumber(turnNumber)
        .userMessage(question)
        .advisorResponse(answer)
        .build());
  }

  // ************************* findForPrompt *************************

  @Test
  void findForPrompt_allWithinDelta_lessThanN() {
    insertExchange(campaign, 10, "q-10", "a-10");
    insertExchange(campaign, 12, "q-12", "a-12");
    insertExchange(campaign, 14, "q-14", "a-14");
    em.flush();
    em.clear();

    List<AdvisorLogProjection> results = advisorLogRepository.findForPrompt(
        campaign.getCharacterId(), 0, PageRequest.of(0, 10));

    assertThat(results).hasSize(3);
    assertThat(results.get(0).turnNumber()).isEqualTo(14);
    assertThat(results.get(1).turnNumber()).isEqualTo(12);
    assertThat(results.get(2).turnNumber()).isEqualTo(10);

    AdvisorLogProjection first = results.get(0);
    assertThat(first.userMessage()).isEqualTo("q-14");
    assertThat(first.advisorResponse()).isEqualTo("a-14");
    assertThat(first.created()).isNotNull();
  }

  @Test
  void findForPrompt_moreThanN_allWithinDelta() {
    for( int i = 1; i <= 5; i++ ){
      insertExchange(campaign, i, "q-" + i, "a-" + i);
    }
    em.flush();
    em.clear();

    List<AdvisorLogProjection> results = advisorLogRepository.findForPrompt(
        campaign.getCharacterId(), 0, PageRequest.of(0, 3));

    assertThat(results).hasSize(3);
    assertThat(results.get(0).turnNumber()).isEqualTo(5);
    assertThat(results.get(1).turnNumber()).isEqualTo(4);
    assertThat(results.get(2).turnNumber()).isEqualTo(3);
  }

  @Test
  void findForPrompt_excludesOutsideDelta() {
    insertExchange(campaign, 5, "q-5", "a-5");
    insertExchange(campaign, 10, "q-10", "a-10");
    insertExchange(campaign, 20, "q-20", "a-20");
    insertExchange(campaign, 25, "q-25", "a-25");
    insertExchange(campaign, 30, "q-30", "a-30");
    em.flush();
    em.clear();

    List<AdvisorLogProjection> results = advisorLogRepository.findForPrompt(
        campaign.getCharacterId(), 18, PageRequest.of(0, 10));

    assertThat(results).hasSize(3);
    assertThat(results.get(0).turnNumber()).isEqualTo(30);
    assertThat(results.get(1).turnNumber()).isEqualTo(25);
    assertThat(results.get(2).turnNumber()).isEqualTo(20);
  }

  @Test
  void findForPrompt_deltaAndLimitCombined() {
    insertExchange(campaign, 5, "q-5", "a-5");
    insertExchange(campaign, 10, "q-10", "a-10");
    insertExchange(campaign, 20, "q-20", "a-20");
    insertExchange(campaign, 22, "q-22", "a-22");
    insertExchange(campaign, 25, "q-25", "a-25");
    insertExchange(campaign, 28, "q-28", "a-28");
    insertExchange(campaign, 30, "q-30", "a-30");
    em.flush();
    em.clear();

    List<AdvisorLogProjection> results = advisorLogRepository.findForPrompt(
        campaign.getCharacterId(), 18, PageRequest.of(0, 3));

    assertThat(results).hasSize(3);
    assertThat(results.get(0).turnNumber()).isEqualTo(30);
    assertThat(results.get(1).turnNumber()).isEqualTo(28);
    assertThat(results.get(2).turnNumber()).isEqualTo(25);
  }

  @Test
  void findForPrompt_boundaryInclusive() {
    insertExchange(campaign, 17, "q-17", "a-17");
    insertExchange(campaign, 18, "q-18", "a-18");
    insertExchange(campaign, 19, "q-19", "a-19");
    em.flush();
    em.clear();

    List<AdvisorLogProjection> results = advisorLogRepository.findForPrompt(
        campaign.getCharacterId(), 18, PageRequest.of(0, 10));

    assertThat(results).hasSize(2);
    assertThat(results.get(0).turnNumber()).isEqualTo(19);
    assertThat(results.get(1).turnNumber()).isEqualTo(18);
  }

  @Test
  void findForPrompt_noExchanges_returnsEmpty() {
    List<AdvisorLogProjection> results = advisorLogRepository.findForPrompt(
        campaign.getCharacterId(), 0, PageRequest.of(0, 10));

    assertThat(results).isEmpty();
  }

  @Test
  void findForPrompt_isolatesByCampaign() {
    // Setup second character + campaign
    String hashB = "test-char-" + UUID.randomUUID().toString().substring(0, 20);
    playerCharacterRepository.insertCharacter(POSTMAN_USER_HASH, "ELF", hashB, "OtherHero", "Another character");
    em.flush();
    em.clear();

    PlayerCharacterEntity characterB = em.createQuery(
            "SELECT pc FROM PlayerCharacterEntity pc WHERE pc.characterHash = :hash", PlayerCharacterEntity.class)
        .setParameter("hash", hashB)
        .getSingleResult();

    CampaignEntity campaignB = CampaignEntity.builder().character(characterB).build();
    campaignRepository.save(campaignB);
    em.flush();
    em.clear();
    campaignB = campaignRepository.findById(characterB.getId()).orElseThrow();

    // Insert exchanges for both campaigns
    insertExchange(campaign, 1, "qA-1", "aA-1");
    insertExchange(campaign, 2, "qA-2", "aA-2");
    insertExchange(campaignB, 1, "qB-1", "aB-1");
    insertExchange(campaignB, 2, "qB-2", "aB-2");
    insertExchange(campaignB, 3, "qB-3", "aB-3");
    em.flush();
    em.clear();

    List<AdvisorLogProjection> results = advisorLogRepository.findForPrompt(
        campaign.getCharacterId(), 0, PageRequest.of(0, 10));

    assertThat(results).hasSize(2);
    assertThat(results.get(0).userMessage()).startsWith("qA-");
    assertThat(results.get(1).userMessage()).startsWith("qA-");
  }

  // ************************* findRecentByCampaign *************************

  @Test
  void findRecentByCampaign_paginatedDesc() {
    insertExchange(campaign, 5, "q-5", "a-5");
    insertExchange(campaign, 10, "q-10", "a-10");
    insertExchange(campaign, 20, "q-20", "a-20");
    insertExchange(campaign, 25, "q-25", "a-25");
    insertExchange(campaign, 30, "q-30", "a-30");
    em.flush();
    em.clear();

    List<AdvisorLogProjection> results = advisorLogRepository.findRecentByCampaign(
        campaign.getCharacterId(), PageRequest.of(0, 3));

    assertThat(results).hasSize(3);
    assertThat(results.get(0).turnNumber()).isEqualTo(30);
    assertThat(results.get(1).turnNumber()).isEqualTo(25);
    assertThat(results.get(2).turnNumber()).isEqualTo(20);
  }

  @Test
  void findRecentByCampaign_allWhenFewerThanLimit() {
    insertExchange(campaign, 1, "q-1", "a-1");
    insertExchange(campaign, 2, "q-2", "a-2");
    insertExchange(campaign, 3, "q-3", "a-3");
    em.flush();
    em.clear();

    List<AdvisorLogProjection> results = advisorLogRepository.findRecentByCampaign(
        campaign.getCharacterId(), PageRequest.of(0, 10));

    assertThat(results).hasSize(3);
    assertThat(results.get(0).turnNumber()).isEqualTo(3);
    assertThat(results.get(1).turnNumber()).isEqualTo(2);
    assertThat(results.get(2).turnNumber()).isEqualTo(1);
  }
}
