package com.ondgard.game.exception;

import com.ondgard.game.model.validation.ApiError;
import lombok.Getter;

@Getter
public class ServiceUnavailableException extends RuntimeException {

  private final ApiError apiError;

  public ServiceUnavailableException(String message) {
    super(message);
    this.apiError = ApiError.fromMessage(message);
  }

  public ServiceUnavailableException(String code, String message) {
    super(message);
    this.apiError = ApiError.fromMessage(code, message);
  }
}
