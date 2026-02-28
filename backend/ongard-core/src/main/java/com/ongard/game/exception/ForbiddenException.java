package com.ongard.game.exception;

import com.ongard.game.model.validation.ApiError;
import lombok.Getter;

@Getter
public class ForbiddenException extends RuntimeException {

  private final ApiError apiError;

  public ForbiddenException(String message) {
    super(message);
    this.apiError = ApiError.fromMessage(message);
  }

  public ForbiddenException(String code, String message) {
    super(message);
    this.apiError = ApiError.fromMessage(code, message);
  }
}
