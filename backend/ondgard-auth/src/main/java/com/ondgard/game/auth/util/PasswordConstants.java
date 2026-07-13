package com.ondgard.game.auth.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor( access = AccessLevel.PRIVATE )
public final class PasswordConstants {

  public static final String PEPPER = "XkP7$mQ2nW";
  public static final int SALT_LENGTH = 32;
}
