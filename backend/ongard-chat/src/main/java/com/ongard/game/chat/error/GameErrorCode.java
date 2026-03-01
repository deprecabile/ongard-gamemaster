package com.ongard.game.chat.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum GameErrorCode {
  INVALID_RACE_CODE("OC_400_01");

  private final String code;

}
