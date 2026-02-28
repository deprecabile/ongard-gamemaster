package com.ongard.game.exception;

import com.ongard.game.model.validation.ApiError;
import lombok.Getter;

@Getter
public class BadRequestException extends IllegalArgumentException {

  private final ApiError apiError;

  public BadRequestException(String message) {
    super(message);
    this.apiError = ApiError.fromMessage(message);
  }

  public BadRequestException(String code, String message) {
    super(message);
    this.apiError = ApiError.fromMessage(code, message);
  }
}
