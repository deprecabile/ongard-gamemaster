package com.ondgard.game.mail.config;

import com.ondgard.game.exception.AppException;
import com.ondgard.game.exception.BadRequestException;
import com.ondgard.game.model.validation.ApiError;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@Slf4j
@ControllerAdvice
public class CustomExceptionInterceptor {

  @ExceptionHandler( BadRequestException.class )
  public ResponseEntity<ApiError> handleBadRequest(BadRequestException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getApiError());
  }

  @ExceptionHandler( AppException.class )
  public ResponseEntity<ApiError> handleAppException(AppException ex) {
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getApiError());
  }

}
