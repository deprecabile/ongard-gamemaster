package com.ondgard.game.chat.repository;

import com.ondgard.game.chat.TestcontainersConfiguration;
import com.ondgard.game.chat.entity.CampaignEntity;
import com.ondgard.game.chat.entity.PlayerCharacterEntity;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class CampaignRepositoryInsertTest extends TestcontainersConfiguration {

  @Autowired private CampaignRepository campaignRepository;
  @Autowired private PlayerCharacterRepository playerCharacterRepository;
  @Autowired private EntityManager em;

  private static final UUID POSTMAN_USER_HASH = UUID.fromString("997a7552-5c1b-42c6-a0c0-094c50a0ad53");

  private PlayerCharacterEntity character;

  @BeforeEach
  void setUp() {
    String hash = "test-char-" + UUID.randomUUID().toString().substring(0, 20);
    playerCharacterRepository.insertCharacter(POSTMAN_USER_HASH, "ELF", hash, "TestHero", "A test character");
    em.flush();
    em.clear();

    character = em.createQuery(
            "SELECT pc FROM PlayerCharacterEntity pc WHERE pc.characterHash = :hash", PlayerCharacterEntity.class)
        .setParameter("hash", hash)
        .getSingleResult();
  }

  @Test
  void insertCampaign_createsRowLinkedToCharacter() {
    campaignRepository.insertCampaign(POSTMAN_USER_HASH, character.getCharacterHash());
    em.flush();
    em.clear();

    Optional<CampaignEntity> found = campaignRepository.findByCharacterHashAndUserHash(
        POSTMAN_USER_HASH, character.getCharacterHash());
    assertThat(found).isPresent();
    CampaignEntity campaign = found.get();
    assertThat(campaign.getCharacterId()).isEqualTo(character.getId());
    assertThat(campaign.getTurnCount()).isZero();
    assertThat(campaign.getSummaryVersion()).isZero();
    assertThat(campaign.getLastSummaryAtTurn()).isZero();
    assertThat(campaign.getNarrativeSummary()).isNull();
    assertThat(campaign.getCreated()).isNotNull();
    assertThat(campaign.getUpdated()).isNotNull();
  }

  @Test
  void insertCampaign_wrongUserHash_insertsNothing() {
    UUID wrongHash = UUID.fromString("00000000-0000-0000-0000-000000000000");
    campaignRepository.insertCampaign(wrongHash, character.getCharacterHash());
    em.flush();
    em.clear();

    Optional<CampaignEntity> found = campaignRepository.findByCharacterHashAndUserHash(
        POSTMAN_USER_HASH, character.getCharacterHash());
    assertThat(found).isEmpty();
  }

  @Test
  void insertCampaign_wrongCharacterHash_insertsNothing() {
    campaignRepository.insertCampaign(POSTMAN_USER_HASH, "nonexistent-hash");
    em.flush();
    em.clear();

    Optional<CampaignEntity> found = campaignRepository.findByCharacterHashAndUserHash(
        POSTMAN_USER_HASH, character.getCharacterHash());
    assertThat(found).isEmpty();
  }

  @Test
  void insertCampaign_duplicate_throwsException() {
    campaignRepository.insertCampaign(POSTMAN_USER_HASH, character.getCharacterHash());
    em.flush();
    em.clear();

    assertThatThrownBy(() -> {
      campaignRepository.insertCampaign(POSTMAN_USER_HASH, character.getCharacterHash());
      em.flush();
    }).isInstanceOf(DataIntegrityViolationException.class);
  }
}
