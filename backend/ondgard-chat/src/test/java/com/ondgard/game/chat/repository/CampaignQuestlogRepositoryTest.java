package com.ondgard.game.chat.repository;

import com.ondgard.game.chat.TestcontainersConfiguration;
import com.ondgard.game.chat.entity.CampaignEntity;
import com.ondgard.game.chat.entity.CampaignQuestlogEntity;
import com.ondgard.game.chat.entity.PlayerCharacterEntity;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class CampaignQuestlogRepositoryTest extends TestcontainersConfiguration {

  @Autowired private CampaignQuestlogRepository campaignQuestlogRepository;
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

  @Test
  void findLatest_returnsHighestTurnNumber() {
    campaignQuestlogRepository.save(CampaignQuestlogEntity.builder()
        .campaign(campaign).turnNumber(1)
        .questActive("- Find the lost sword").questCompleted(null).build());
    campaignQuestlogRepository.save(CampaignQuestlogEntity.builder()
        .campaign(campaign).turnNumber(5)
        .questActive("- Find the lost sword\n- Talk to the merchant").questCompleted(null).build());
    campaignQuestlogRepository.save(CampaignQuestlogEntity.builder()
        .campaign(campaign).turnNumber(10)
        .questActive("- Talk to the merchant").questCompleted("- Find the lost sword").build());
    em.flush();
    em.clear();

    Optional<CampaignQuestlogEntity> latest = campaignQuestlogRepository.findLatestByCampaignId(
        campaign.getCharacterId());

    assertThat(latest).isPresent();
    assertThat(latest.get().getTurnNumber()).isEqualTo(10);
    assertThat(latest.get().getQuestActive()).isEqualTo("- Talk to the merchant");
    assertThat(latest.get().getQuestCompleted()).isEqualTo("- Find the lost sword");
  }

  @Test
  void findLatest_noQuestlog_returnsEmpty() {
    Optional<CampaignQuestlogEntity> latest = campaignQuestlogRepository.findLatestByCampaignId(
        campaign.getCharacterId());

    assertThat(latest).isEmpty();
  }
}
