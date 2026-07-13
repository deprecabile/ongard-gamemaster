package com.ondgard.game.chat.contract.sse;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SseProgressCode implements SseProgressValue {

  // Turn pipeline (GameMasterService)
  GM_WRITING("GM_WRITING"),
  GM_INCONSISTENCY("GM_INCONSISTENCY"),
  GM_REWRITING("GM_REWRITING"),
  VALIDATORS_RUNNING("VALIDATORS_RUNNING"),
  UPDATERS_RUNNING("UPDATERS_RUNNING"),

  // Campaign init (CampaignInitService)
  CAMPAIGN_PREPARING("CAMPAIGN_PREPARING"),
  INIT_AGENTS("INIT_AGENTS"),
  AGENTS_WORKING("AGENTS_WORKING"),
  CAMPAIGN_SAVING("CAMPAIGN_SAVING"),

  // Advisor (AdvisorService)
  ADVISOR_THINKING("ADVISOR_THINKING"),

  // Setup advisor (CampaignSetupInteractionService)
  SETUP_ADVISOR_THINKING("SETUP_ADVISOR_THINKING");

  private final String value;
}
