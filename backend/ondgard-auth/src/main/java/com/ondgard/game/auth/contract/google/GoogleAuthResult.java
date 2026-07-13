package com.ondgard.game.auth.contract.google;

import com.ondgard.game.auth.contract.LoginResponse;

public sealed interface GoogleAuthResult {

  record LoginSuccess( LoginResponse response ) implements GoogleAuthResult {
  }

  record RegistrationRequired( GoogleRegistrationRequiredResponse response ) implements GoogleAuthResult {
  }
}
