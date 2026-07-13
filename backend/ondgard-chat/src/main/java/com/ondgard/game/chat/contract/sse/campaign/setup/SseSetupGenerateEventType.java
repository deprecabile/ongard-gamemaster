package com.ondgard.game.chat.contract.sse.campaign.setup;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SseSetupGenerateEventType {

  STARTED("setup_generate_started"),
  PROGRESS("setup_generate_progress"),
  NAME_PICKED("setup_generate_name_picked"),
  CHARACTER_GENERATED("setup_generate_character_generated"),
  COMPLETED("setup_generate_completed"),
  ERROR("setup_generate_error");

  private final String value;
}
