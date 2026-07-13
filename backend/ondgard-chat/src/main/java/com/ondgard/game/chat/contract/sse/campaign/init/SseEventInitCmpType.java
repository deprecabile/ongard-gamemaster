package com.ondgard.game.chat.contract.sse.campaign.init;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SseEventInitCmpType {

  STARTED("started"),
  PROGRESS("progress"),
  COMPLETED("completed"),
  ERROR("error");

  private final String value;
}
