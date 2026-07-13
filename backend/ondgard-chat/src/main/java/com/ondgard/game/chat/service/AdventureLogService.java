package com.ondgard.game.chat.service;

import com.ondgard.game.chat.entity.AdventureLogEntity;
import com.ondgard.game.chat.entity.CampaignEntity;
import com.ondgard.game.chat.model.ChatEntry;
import com.ondgard.game.chat.repository.AdventureLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdventureLogService {

  private final AdventureLogRepository adventureLogRepository;

  @Async
  public void persistTurnAsync(Long campaignId, ChatEntry entry) {
    persistTurn(campaignId, entry);
  }

  @Transactional
  public void persistTurn(Long campaignId, ChatEntry entry) {
    CampaignEntity campaignRef = CampaignEntity.builder().characterId(campaignId).build();

    AdventureLogEntity userLog = AdventureLogEntity.builder()
        .campaign(campaignRef)
        .role("USER")
        .content(entry.userMessage())
        .turnNumber(entry.turnNumber())
        .build();

    AdventureLogEntity gmLog = AdventureLogEntity.builder()
        .campaign(campaignRef)
        .role("GM")
        .content(entry.gmResponse())
        .turnNumber(entry.turnNumber())
        .build();

    adventureLogRepository.save(userLog);
    adventureLogRepository.save(gmLog);
    log.debug("Adventure log persisted for campaignId={}, turn={}", campaignId, entry.turnNumber());
  }
}
