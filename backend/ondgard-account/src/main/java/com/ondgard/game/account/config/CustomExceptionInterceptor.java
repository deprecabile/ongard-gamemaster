package com.ondgard.game.account.config;

import com.ondgard.game.exception.AppException;
import com.ondgard.game.exception.BadRequestException;
import com.ondgard.game.exception.ConflictException;
import com.ondgard.game.exception.NoResultException;
import com.ondgard.game.model.validation.ApiError;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
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

  @ExceptionHandler( ConflictException.class )
  public ResponseEntity<ApiError> handleConflict(ConflictException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getApiError());
  }

  @ExceptionHandler( NoResultException.class )
  public ResponseEntity<Void> handleNoResult(NoResultException ex) {
    return ResponseEntity.noContent().build();
  }

  @ExceptionHandler( DataIntegrityViolationException.class )
  public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex) {
    log.error(ex.getMessage(), ex);
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(ApiError.fromMessage("OA_409_00", "Resource already exists"));
  }

  @ExceptionHandler( AppException.class )
  public ResponseEntity<ApiError> handleAppException(AppException ex) {
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getApiError());
  }
}
