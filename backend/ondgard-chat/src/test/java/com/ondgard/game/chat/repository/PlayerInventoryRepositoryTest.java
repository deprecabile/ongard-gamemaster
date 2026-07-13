package com.ondgard.game.chat.repository;

import com.ondgard.game.chat.TestcontainersConfiguration;
import com.ondgard.game.chat.entity.CampaignEntity;
import com.ondgard.game.chat.entity.PlayerCharacterEntity;
import com.ondgard.game.chat.entity.PlayerInventoryEntity;
import com.ondgard.game.chat.model.inventory.*;
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
class PlayerInventoryRepositoryTest extends TestcontainersConfiguration {

  @Autowired private PlayerInventoryRepository playerInventoryRepository;
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
  void findLatest_returnsHighestVersion() {
    playerInventoryRepository.save(PlayerInventoryEntity.builder()
        .campaign(campaign).version(1).turnNumber(1).inventory(Inventory.empty()).build());
    playerInventoryRepository.save(PlayerInventoryEntity.builder()
        .campaign(campaign).version(2).turnNumber(3).inventory(
            Inventory.builder()
                .money(5000)
                .contenitori(List.of(Contenitore.builder()
                    .nome("Zaino")
                    .oggetti(List.of(InventoryItem.builder().nome("Sword").quantita(1).peso(3.0).build()))
                    .build()))
                .build()
        ).build());
    playerInventoryRepository.save(PlayerInventoryEntity.builder()
        .campaign(campaign).version(3).turnNumber(5).inventory(
            Inventory.builder()
                .money(10005)
                .contenitori(List.of(Contenitore.builder()
                    .nome("Zaino")
                    .oggetti(List.of(
                        InventoryItem.builder().nome("Sword").quantita(1).peso(3.0).build(),
                        InventoryItem.builder().nome("Shield").quantita(1).peso(5.0).build()
                    ))
                    .build()))
                .build()
        ).build());
    em.flush();
    em.clear();

    Optional<PlayerInventoryEntity> latest = playerInventoryRepository.findLatestByCampaignId(
        campaign.getCharacterId());

    assertThat(latest).isPresent();
    assertThat(latest.get().getVersion()).isEqualTo(3);
    assertThat(latest.get().getInventory().getMoney()).isEqualTo(10005);
    assertThat(latest.get().getInventory().getContenitori()).hasSize(1);
  }

  @Test
  void findLatest_noInventory_returnsEmpty() {
    Optional<PlayerInventoryEntity> latest = playerInventoryRepository.findLatestByCampaignId(
        campaign.getCharacterId());

    assertThat(latest).isEmpty();
  }

  @Test
  void jsonbPersistence_roundTrips() {
    Inventory inv = Inventory.builder()
        .money(10005)
        .mount(List.of(
            Mount.builder().nome("Cavallo da guerra").tipo("cavalcatura").stato("sano").build()
        ))
        .contenitori(List.of(
            Contenitore.builder()
                .nome("Zaino")
                .oggetti(List.of(
                    InventoryItem.builder().nome("Elven Bow").descrizione("An enchanted bow").quantita(1).peso(1.5)
                        .extra(List.of(ExtraProperty.builder().chiave("enchanted").valore("true").build()))
                        .build(),
                    InventoryItem.builder().nome("Health Potion").quantita(3).peso(0.5).build()
                ))
                .build()
        ))
        .build();

    playerInventoryRepository.save(PlayerInventoryEntity.builder()
        .campaign(campaign).version(1).turnNumber(1).inventory(inv).build());
    em.flush();
    em.clear();

    Optional<PlayerInventoryEntity> loaded = playerInventoryRepository.findLatestByCampaignId(
        campaign.getCharacterId());

    assertThat(loaded).isPresent();
    Inventory stored = loaded.get().getInventory();
    assertThat(stored.getMoney()).isEqualTo(10005);
    assertThat(stored.getMount()).hasSize(1);
    assertThat(stored.getContenitori()).hasSize(1);

    Contenitore zaino = stored.getContenitori().iterator().next();
    assertThat(zaino.getNome()).isEqualTo("Zaino");
    assertThat(zaino.getOggetti()).hasSize(2);
  }
}
