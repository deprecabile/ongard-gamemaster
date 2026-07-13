package com.ondgard.game.chat.config.ai;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum LoreMetadataTag {
  SCENARIO("scenario"),
  LANGUAGE("language"),
  CATEGORY("category");

  private final String key;
}
