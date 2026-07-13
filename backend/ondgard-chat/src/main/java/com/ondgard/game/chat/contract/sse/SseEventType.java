package com.ondgard.game.chat.contract.sse;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SseEventType {

  STARTED("started"),
  PROGRESS("progress"),
  COMPLETED("completed"),
  ERROR("error");

  private final String value;
}
