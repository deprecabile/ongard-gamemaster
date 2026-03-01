package com.ongard.game.chat.config;

import com.ongard.game.exception.*;
import com.ongard.game.model.validation.ApiError;
import lombok.extern.log4j.Log4j2;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@Log4j2
@ControllerAdvice
public class CustomExceptionInterceptor {

  @ExceptionHandler( BadRequestException.class )
  public ResponseEntity<ApiError> handleBadRequestException(BadRequestException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getApiError());
  }

  @ExceptionHandler( ForbiddenException.class )
  public ResponseEntity<ApiError> handleForbiddenException(ForbiddenException ex) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ex.getApiError());
  }

  @ExceptionHandler( ConflictException.class )
  public ResponseEntity<ApiError> handleConflictException(ConflictException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getApiError());
  }

  @ExceptionHandler( NoResultException.class )
  public ResponseEntity<Void> handleNoResultException(NoResultException ex) {
    return ResponseEntity.noContent().build();
  }

  @ExceptionHandler( DataIntegrityViolationException.class )
  public ResponseEntity<ApiError> handleDataIntegrityViolationException(DataIntegrityViolationException ex) {
    log.error(ex);
    return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.fromMessage("OC_409_00", "Something went wrong"));
  }

  @ExceptionHandler( AppException.class )
  public ResponseEntity<ApiError> handleAppException(AppException ex) {
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getApiError());
  }
}
