package com.ondgard.game.exception;

import com.ondgard.game.model.validation.ApiError;
import lombok.Getter;

@Getter
public class UnauthorizedException extends RuntimeException {

  private final ApiError apiError;

  public UnauthorizedException(String message) {
    super(message);
    this.apiError = ApiError.fromMessage(message);
  }

  public UnauthorizedException(String code, String message) {
    super(message);
    this.apiError = ApiError.fromMessage(code, message);
  }
}
