package com.ondgard.game.chat.contract.sse.campaign.ask;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SseAdvisorEventType {

  STARTED("advisor_started"),
  THINKING("advisor_thinking"),
  COMPLETED("advisor_completed"),
  ERROR("advisor_error");

  private final String value;
}
