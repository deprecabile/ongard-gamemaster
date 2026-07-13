package com.ondgard.game.exception;

import com.ondgard.game.model.validation.ApiError;
import com.ondgard.game.model.validation.Message;
import lombok.Getter;

import java.util.Collection;

@Getter
public class AppException extends RuntimeException {
  private final ApiError apiError;

  public AppException(String message) {
    super(message);
    this.apiError = ApiError.fromMessage(message);
  }

  public AppException(Collection<Message> messages) {
    super(messages.iterator().next().getMessage());
    this.apiError = new ApiError(messages);
  }
}
