package com.ondgard.game.chat.contract.sse.campaign.setup;

import com.ondgard.game.chat.contract.sse.SseProgressValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SseSetupGenerateProgressCode implements SseProgressValue {

  SETUP_SUMMARIZING("SETUP_SUMMARIZING"),
  SETUP_GENERATING_CHARACTER("SETUP_GENERATING_CHARACTER"),
  SETUP_PICKING_NAME("SETUP_PICKING_NAME"),
  SETUP_GENERATING_SCENE("SETUP_GENERATING_SCENE");

  private final String value;
}
