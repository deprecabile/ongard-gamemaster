package com.ondgard.game.chat.repository;

import com.ondgard.game.chat.TestcontainersConfiguration;
import com.ondgard.game.chat.entity.CampaignEntity;
import com.ondgard.game.chat.entity.PlayerCharacterEntity;
import com.ondgard.game.chat.repository.projection.CampaignListProjection;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class CampaignRepositoryTest extends TestcontainersConfiguration {

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
  void createCampaign_persistsWithSharedPK() {
    CampaignEntity campaign = CampaignEntity.builder()
        .character(character)
        .build();
    campaignRepository.save(campaign);
    em.flush();
    em.clear();

    Optional<CampaignEntity> found = campaignRepository.findById(character.getId());
    assertThat(found).isPresent();
    CampaignEntity loaded = found.get();
    assertThat(loaded.getCharacterId()).isEqualTo(character.getId());
    assertThat(loaded.getTurnCount()).isZero();
    assertThat(loaded.getSummaryVersion()).isZero();
    assertThat(loaded.getLastSummaryAtTurn()).isZero();
    assertThat(loaded.getNarrativeSummary()).isNull();
    assertThat(loaded.getCreated()).isNotNull();
    assertThat(loaded.getUpdated()).isNotNull();
  }

  @Test
  void updateCampaign_modifiesFields() {
    CampaignEntity campaign = CampaignEntity.builder()
        .character(character)
        .build();
    campaignRepository.save(campaign);
    em.flush();
    em.clear();

    CampaignEntity toUpdate = campaignRepository.findById(character.getId()).orElseThrow();
    toUpdate.setTurnCount(5);
    toUpdate.setNarrativeSummary("The hero ventured into the dark forest.");
    toUpdate.setCurrentLocation("Dark Forest");
    toUpdate.setGameDate("12 Solara 1247");
    toUpdate.setGameTime("Dusk");
    toUpdate.setMeteo("Foggy");
    toUpdate.setTemperature("Cold");
    campaignRepository.save(toUpdate);
    em.flush();
    em.clear();

    CampaignEntity reloaded = campaignRepository.findById(character.getId()).orElseThrow();
    assertThat(reloaded.getTurnCount()).isEqualTo(5);
    assertThat(reloaded.getNarrativeSummary()).isEqualTo("The hero ventured into the dark forest.");
    assertThat(reloaded.getCurrentLocation()).isEqualTo("Dark Forest");
    assertThat(reloaded.getGameDate()).isEqualTo("12 Solara 1247");
    assertThat(reloaded.getGameTime()).isEqualTo("Dusk");
    assertThat(reloaded.getMeteo()).isEqualTo("Foggy");
    assertThat(reloaded.getTemperature()).isEqualTo("Cold");
  }

  @Test
  void uniqueness_rejectsDuplicateCampaignForSameCharacter() {
    CampaignEntity first = CampaignEntity.builder()
        .character(character)
        .build();
    campaignRepository.save(first);
    em.flush();
    em.clear();

    CampaignEntity duplicate = CampaignEntity.builder()
        .character(character)
        .build();

    assertThatThrownBy(() -> {
      campaignRepository.save(duplicate);
      em.flush();
    }).isInstanceOf(PersistenceException.class);
  }

  @Test
  void findByCharacterHashAndUserHash_found() {
    CampaignEntity campaign = CampaignEntity.builder()
        .character(character)
        .build();
    campaignRepository.save(campaign);
    em.flush();
    em.clear();

    Optional<CampaignEntity> found = campaignRepository.findByCharacterHashAndUserHash(
        POSTMAN_USER_HASH, character.getCharacterHash());
    assertThat(found).isPresent();
    assertThat(found.get().getCharacterId()).isEqualTo(character.getId());
  }

  @Test
  void findByCharacterHashAndUserHash_wrongUserHash() {
    CampaignEntity campaign = CampaignEntity.builder()
        .character(character)
        .build();
    campaignRepository.save(campaign);
    em.flush();
    em.clear();

    UUID wrongHash = UUID.fromString("00000000-0000-0000-0000-000000000000");
    Optional<CampaignEntity> found = campaignRepository.findByCharacterHashAndUserHash(
        wrongHash, character.getCharacterHash());
    assertThat(found).isEmpty();
  }

  @Test
  void findByCharacterHashAndUserHash_wrongCharacterHash() {
    CampaignEntity campaign = CampaignEntity.builder()
        .character(character)
        .build();
    campaignRepository.save(campaign);
    em.flush();
    em.clear();

    Optional<CampaignEntity> found = campaignRepository.findByCharacterHashAndUserHash(
        POSTMAN_USER_HASH, "nonexistent-hash");
    assertThat(found).isEmpty();
  }

  // ************************* findAllByUserHash *************************

  @Test
  void findAllByUserHash_returnsCampaignsWithTurnCountGreaterThanZero() {
    CampaignEntity campaign = CampaignEntity.builder()
        .character(character)
        .build();
    campaignRepository.save(campaign);
    em.flush();

    CampaignEntity toUpdate = campaignRepository.findById(character.getId()).orElseThrow();
    toUpdate.setTurnCount(5);
    toUpdate.setCurrentLocation("Ancient Temple");
    toUpdate.setNarrativeSummary("A hero ventured forth.");
    campaignRepository.save(toUpdate);
    em.flush();
    em.clear();

    List<CampaignListProjection> results = campaignRepository.findAllByUserHash(POSTMAN_USER_HASH);

    assertThat(results).anySatisfy(p -> {
      assertThat(p.characterHash()).isEqualTo(character.getCharacterHash());
      assertThat(p.characterName()).isEqualTo("TestHero");
      assertThat(p.raceCode()).isEqualTo("ELF");
      assertThat(p.turnCount()).isEqualTo(5);
      assertThat(p.currentLocation()).isEqualTo("Ancient Temple");
      assertThat(p.narrativeSummary()).isEqualTo("A hero ventured forth.");
      assertThat(p.description()).isEqualTo("A test character");
    });
  }

  @Test
  void findAllByUserHash_excludesZeroTurnCampaigns() {
    CampaignEntity campaign = CampaignEntity.builder()
        .character(character)
        .build();
    campaignRepository.save(campaign);
    em.flush();
    em.clear();

    List<CampaignListProjection> results = campaignRepository.findAllByUserHash(POSTMAN_USER_HASH);

    assertThat(results).noneMatch(p -> p.characterHash().equals(character.getCharacterHash()));
  }

  @Test
  void findAllByUserHash_wrongUser_returnsEmpty() {
    CampaignEntity campaign = CampaignEntity.builder()
        .character(character)
        .build();
    campaignRepository.save(campaign);
    em.flush();

    CampaignEntity toUpdate = campaignRepository.findById(character.getId()).orElseThrow();
    toUpdate.setTurnCount(3);
    campaignRepository.save(toUpdate);
    em.flush();
    em.clear();

    UUID wrongHash = UUID.fromString("00000000-0000-0000-0000-000000000000");
    List<CampaignListProjection> results = campaignRepository.findAllByUserHash(wrongHash);

    assertThat(results).isEmpty();
  }
}
