package com.ondgard.game.exception;

import com.ondgard.game.contract.account.CheckLimitResponse;
import com.ondgard.game.model.validation.ApiError;
import lombok.Getter;

@Getter
public class TokenLimitExceededException extends RuntimeException {

  private final ApiError apiError;
  private final String limitType;

  public TokenLimitExceededException(String code, String message, String limitType) {
    super(message);
    this.apiError = ApiError.fromMessage(code, message);
    this.limitType = limitType;
  }

  public TokenLimitExceededException(String code, CheckLimitResponse limitCheck) {
    this(code, "Token limit exceeded: " + limitCheck.type(), limitCheck.type());
  }

}
