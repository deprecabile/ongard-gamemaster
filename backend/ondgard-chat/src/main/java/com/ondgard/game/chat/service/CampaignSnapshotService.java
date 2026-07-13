package com.ondgard.game.chat.service;

import com.ondgard.game.chat.entity.CampaignEntity;
import com.ondgard.game.chat.entity.CampaignQuestlogEntity;
import com.ondgard.game.chat.entity.PlayerInventoryEntity;
import com.ondgard.game.chat.model.CampaignQuestLog;
import com.ondgard.game.chat.model.inventory.Inventory;
import com.ondgard.game.chat.repository.CampaignQuestlogRepository;
import com.ondgard.game.chat.repository.PlayerInventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CampaignSnapshotService {

  private final PlayerInventoryRepository inventoryRepo;
  private final CampaignQuestlogRepository questlogRepo;

  @Async
  @Transactional
  public void persistInventoryAsync(Long campaignId, int turnNumber, Inventory inventory) {
    int nextVersion = inventoryRepo.findLatestByCampaignId(campaignId)
        .map(e -> e.getVersion() + 1).orElse(1);

    inventoryRepo.save(PlayerInventoryEntity.builder()
        .campaign(CampaignEntity.builder().characterId(campaignId).build())
        .version(nextVersion)
        .turnNumber(turnNumber)
        .inventory(inventory)
        .build());

    log.debug("Inventory persisted for campaignId={}, turn={}, version={}", campaignId, turnNumber, nextVersion);
  }

  @Async
  @Transactional
  public void persistQuestlogAsync(Long campaignId, int turnNumber, CampaignQuestLog questLog) {
    questlogRepo.save(CampaignQuestlogEntity.builder()
        .campaign(CampaignEntity.builder().characterId(campaignId).build())
        .turnNumber(turnNumber)
        .questActive(questLog.questActive())
        .questCompleted(questLog.questCompleted())
        .build());

    log.debug("Questlog persisted for campaignId={}, turn={}", campaignId, turnNumber);
  }
}
