package com.ondgard.game.chat.service;

import com.ondgard.game.chat.TestcontainersConfiguration;
import com.ondgard.game.chat.entity.AdventureLogEntity;
import com.ondgard.game.chat.model.ChatEntry;
import com.ondgard.game.chat.repository.AdventureLogRepository;
import com.ondgard.game.chat.repository.CampaignRepository;
import com.ondgard.game.chat.repository.PlayerCharacterRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Collection;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AdventureLogServiceTest extends TestcontainersConfiguration {

  @Autowired private AdventureLogService adventureLogService;
  @Autowired private AdventureLogRepository adventureLogRepository;
  @Autowired private PlayerCharacterRepository playerCharacterRepository;
  @Autowired private CampaignRepository campaignRepository;
  @Autowired private PlatformTransactionManager txManager;

  private static final UUID USER_HASH = UUID.fromString("997a7552-5c1b-42c6-a0c0-094c50a0ad53");

  private Long campaignId;
  private String characterHash;

  @BeforeEach
  void setUp() {
    characterHash = "test-advlog-" + UUID.randomUUID().toString().substring(0, 16);
    new TransactionTemplate(txManager).executeWithoutResult(status -> {
      playerCharacterRepository.insertCharacter(USER_HASH, "ELF", characterHash, "LogHero", "A test character");
      campaignRepository.insertCampaign(USER_HASH, characterHash);
    });
    campaignId = campaignRepository.findByCharacterHashAndUserHash(USER_HASH, characterHash)
        .orElseThrow().getCharacterId();
  }

  @AfterEach
  void cleanup() {
    new TransactionTemplate(txManager).executeWithoutResult(status -> {
      adventureLogRepository.deleteAll(adventureLogRepository.findAll().stream()
          .filter(e -> e.getCampaign().getCharacterId().equals(campaignId))
          .toList());
      campaignRepository.deleteById(campaignId);
    });
  }

  @Test
  void persistTurn_savesBothUserAndGmEntries() {
    var entry = new ChatEntry(1, "I open the chest", "Inside you find a golden key.");

    adventureLogService.persistTurn(campaignId, entry);

    Collection<AdventureLogEntity> all = adventureLogRepository.findAll().stream()
        .filter(e -> e.getCampaign().getCharacterId().equals(campaignId))
        .toList();

    assertThat(all).hasSize(2);

    AdventureLogEntity userLog = all.stream().filter(e -> "USER".equals(e.getRole())).findFirst().orElseThrow();
    assertThat(userLog.getContent()).isEqualTo("I open the chest");
    assertThat(userLog.getTurnNumber()).isEqualTo(1);

    AdventureLogEntity gmLog = all.stream().filter(e -> "GM".equals(e.getRole())).findFirst().orElseThrow();
    assertThat(gmLog.getContent()).isEqualTo("Inside you find a golden key.");
    assertThat(gmLog.getTurnNumber()).isEqualTo(1);
  }

  @Test
  void persistTurn_multipleTurns_maintainsTurnNumbers() {
    adventureLogService.persistTurn(campaignId, new ChatEntry(1, "action-1", "response-1"));
    adventureLogService.persistTurn(campaignId, new ChatEntry(2, "action-2", "response-2"));
    adventureLogService.persistTurn(campaignId, new ChatEntry(3, "action-3", "response-3"));

    Collection<AdventureLogEntity> all = adventureLogRepository.findAll().stream()
        .filter(e -> e.getCampaign().getCharacterId().equals(campaignId))
        .toList();

    assertThat(all).hasSize(6);
    assertThat(all.stream().filter(e -> "USER".equals(e.getRole())).count()).isEqualTo(3);
    assertThat(all.stream().filter(e -> "GM".equals(e.getRole())).count()).isEqualTo(3);
  }
}
