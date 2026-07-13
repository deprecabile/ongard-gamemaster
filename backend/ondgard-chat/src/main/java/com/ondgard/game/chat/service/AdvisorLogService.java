package com.ondgard.game.chat.service;

import com.ondgard.game.chat.config.AdvisorProperties;
import com.ondgard.game.chat.entity.AdvisorLogEntity;
import com.ondgard.game.chat.entity.CampaignEntity;
import com.ondgard.game.chat.repository.AdvisorLogRepository;
import com.ondgard.game.chat.repository.projection.AdvisorLogProjection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdvisorLogService {

  private final AdvisorLogRepository advisorLogRepository;
  private final AdvisorProperties advisorProps;

  public void persist(Long campaignId, int turnNumber, String userMessage, String advisorResponse) {
    final CampaignEntity campaignRef = CampaignEntity.builder().characterId(campaignId).build();
    advisorLogRepository.save(AdvisorLogEntity.builder()
        .campaign(campaignRef)
        .turnNumber(turnNumber)
        .userMessage(userMessage)
        .advisorResponse(advisorResponse)
        .build());
    log.debug("Advisor log persisted for campaignId={}, turn={}", campaignId, turnNumber);
  }

  @Async
  public void persistAsync(Long campaignId, int turnNumber, String userMessage, String advisorResponse) {
    persist(campaignId, turnNumber, userMessage, advisorResponse);
  }

  /**
   * Ultimi M scambi per il frontend (ordine cronologico).
   */
  public List<AdvisorLogProjection> findRecent(Long campaignId) {
    List<AdvisorLogProjection> desc = advisorLogRepository.findRecentByCampaign(
        campaignId, PageRequest.of(0, advisorProps.getMaxDisplayExchanges()));
    return desc.reversed();
  }

  /**
   * Scambi per il prompt (filtro N + D, ordine cronologico).
   */
  public List<AdvisorLogProjection> findForPrompt(Long campaignId, int currentTurn) {
    int minTurn = Math.max(0, currentTurn - advisorProps.getMaxTurnDelta());
    List<AdvisorLogProjection> desc = advisorLogRepository.findForPrompt(
        campaignId, minTurn, PageRequest.of(0, advisorProps.getMaxHistoryExchanges()));
    return desc.reversed();
  }
}
