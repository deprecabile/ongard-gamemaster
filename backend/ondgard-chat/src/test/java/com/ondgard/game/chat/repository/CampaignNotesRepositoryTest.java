package com.ondgard.game.chat.repository;

import com.ondgard.game.chat.TestcontainersConfiguration;
import com.ondgard.game.chat.entity.CampaignEntity;
import com.ondgard.game.chat.entity.CampaignNotesEntity;
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
class CampaignNotesRepositoryTest extends TestcontainersConfiguration {

  @Autowired private CampaignNotesRepository campaignNotesRepository;
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

  // ************************* insertEmpty *************************

  @Test
  void insertEmpty_createsRowWithEmptyContent() {
    campaignNotesRepository.insertEmpty(campaign.getCharacterId());
    em.flush();
    em.clear();

    Optional<CampaignNotesEntity> found = campaignNotesRepository.findById(campaign.getCharacterId());
    assertThat(found).isPresent();
    CampaignNotesEntity notes = found.get();
    assertThat(notes.getCampaignId()).isEqualTo(campaign.getCharacterId());
    assertThat(notes.getContent()).isEmpty();
    assertThat(notes.getUpdated()).isNotNull();
  }

  @Test
  void insertEmpty_duplicate_throwsException() {
    campaignNotesRepository.insertEmpty(campaign.getCharacterId());
    em.flush();
    em.clear();

    assertThatThrownBy(() -> {
      campaignNotesRepository.insertEmpty(campaign.getCharacterId());
      em.flush();
    }).isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void insertEmpty_nonExistentCampaign_throwsException() {
    assertThatThrownBy(() -> {
      campaignNotesRepository.insertEmpty(-999L);
      em.flush();
    }).isInstanceOf(DataIntegrityViolationException.class);
  }

  // ************************* updateContent *************************

  @Test
  void updateContent_updatesContentAndTimestamp() {
    campaignNotesRepository.insertEmpty(campaign.getCharacterId());
    em.flush();
    em.clear();

    CampaignNotesEntity before = campaignNotesRepository.findById(campaign.getCharacterId()).orElseThrow();
    var updatedBefore = before.getUpdated();

    campaignNotesRepository.updateContent(campaign.getCharacterId(), "My adventure notes");
    em.flush();
    em.clear();

    CampaignNotesEntity after = campaignNotesRepository.findById(campaign.getCharacterId()).orElseThrow();
    assertThat(after.getContent()).isEqualTo("My adventure notes");
    assertThat(after.getUpdated()).isAfterOrEqualTo(updatedBefore);
  }

  @Test
  void updateContent_overwritesPreviousContent() {
    campaignNotesRepository.insertEmpty(campaign.getCharacterId());
    em.flush();

    campaignNotesRepository.updateContent(campaign.getCharacterId(), "First draft");
    em.flush();

    campaignNotesRepository.updateContent(campaign.getCharacterId(), "Final version");
    em.flush();
    em.clear();

    CampaignNotesEntity notes = campaignNotesRepository.findById(campaign.getCharacterId()).orElseThrow();
    assertThat(notes.getContent()).isEqualTo("Final version");
  }

  @Test
  void updateContent_nonExistentCampaign_updatesNothing() {
    campaignNotesRepository.insertEmpty(campaign.getCharacterId());
    em.flush();

    campaignNotesRepository.updateContent(-999L, "Should not persist");
    em.flush();
    em.clear();

    CampaignNotesEntity notes = campaignNotesRepository.findById(campaign.getCharacterId()).orElseThrow();
    assertThat(notes.getContent()).isEmpty();
  }

  // ************************* findByCampaignId *************************

  @Test
  void findByCampaignId_returnsExistingNotes() {
    campaignNotesRepository.insertEmpty(campaign.getCharacterId());
    em.flush();

    campaignNotesRepository.updateContent(campaign.getCharacterId(), "Some notes");
    em.flush();
    em.clear();

    Optional<CampaignNotesEntity> found = campaignNotesRepository.findByCampaignId(campaign.getCharacterId());
    assertThat(found).isPresent();
    assertThat(found.get().getContent()).isEqualTo("Some notes");
  }

  @Test
  void findByCampaignId_noNotes_returnsEmpty() {
    Optional<CampaignNotesEntity> found = campaignNotesRepository.findByCampaignId(campaign.getCharacterId());
    assertThat(found).isEmpty();
  }

  @Test
  void findByCampaignId_nonExistentCampaign_returnsEmpty() {
    Optional<CampaignNotesEntity> found = campaignNotesRepository.findByCampaignId(-999L);
    assertThat(found).isEmpty();
  }

  // ************************* cascade delete *************************

  @Test
  void deleteCampaign_cascadeDeletesNotes() {
    campaignNotesRepository.insertEmpty(campaign.getCharacterId());
    em.flush();
    em.clear();

    assertThat(campaignNotesRepository.findById(campaign.getCharacterId())).isPresent();

    em.createNativeQuery("DELETE FROM campaign WHERE character_id = :id")
        .setParameter("id", campaign.getCharacterId())
        .executeUpdate();
    em.flush();
    em.clear();

    assertThat(campaignNotesRepository.findById(campaign.getCharacterId())).isEmpty();
  }
}
