package com.ondgard.game.chat.contract.sse.campaign.setup;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SseSetupInteractionEventType {

  STARTED("setup_interaction_started"),
  THINKING("setup_interaction_thinking"),
  COMPLETED("setup_interaction_completed"),
  ERROR("setup_interaction_error");

  private final String value;
}
