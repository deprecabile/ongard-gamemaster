package com.ondgard.game.chat.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum GameErrorCode {
  INVALID_RACE_CODE("OC_400_01"),
  MISSING_CHARACTER_HASH("OC_400_02"),
  CAMPAIGN_NOT_FOUND("OC_404_01"),
  CAMPAIGN_INIT_FAILED("OC_500_01"),
  RAG_NOT_READY("OC_503_01"),
  TOKEN_LIMIT_EXCEEDED("OC_429_01");

  private final String code;

}
