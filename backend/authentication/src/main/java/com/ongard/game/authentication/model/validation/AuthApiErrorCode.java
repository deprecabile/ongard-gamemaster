package com.ongard.game.authentication.model.validation;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor( access = AccessLevel.PRIVATE )
public final class AuthApiErrorCode {

  public static final String GENERIC_ERROR = "AU_500_00";
  public static final String USERNAME_TAKEN = "AU_400_01";
  public static final String EMAIL_TAKEN = "AU_400_02";
  public static final String INVALID_CREDENTIALS = "AU_401_01";
  public static final String INVALID_REFRESH_TOKEN = "AU_401_02";
  public static final String EXPIRED_REFRESH_TOKEN = "AU_401_03";
}
