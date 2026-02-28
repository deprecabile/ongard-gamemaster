package com.ongard.game.authentication.config;

import com.ongard.game.exception.AppException;
import com.ongard.game.exception.BadRequestException;
import com.ongard.game.exception.UnauthorizedException;
import com.ongard.game.model.validation.ApiError;
import com.ongard.game.model.validation.Message;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.List;

@ControllerAdvice
public class CustomExceptionInterceptor {

  @ExceptionHandler( BadRequestException.class )
  public ResponseEntity<ApiError> handleBadRequestException(BadRequestException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getApiError());
  }

  @ExceptionHandler( UnauthorizedException.class )
  public ResponseEntity<ApiError> handleUnauthorizedException(UnauthorizedException ex) {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ex.getApiError());
  }

  @ExceptionHandler( AppException.class )
  public ResponseEntity<ApiError> handleAppException(AppException ex) {
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getApiError());
  }

  @ExceptionHandler( MethodArgumentNotValidException.class )
  public ResponseEntity<ApiError> handleValidationException(MethodArgumentNotValidException ex) {
    List<Message> messages = ex.getBindingResult().getFieldErrors().stream()
        .map(error -> Message.builder()
            .level(Message.Level.ERROR)
            .code("AU_400_00")
            .message(error.getField() + ": " + error.getDefaultMessage())
            .build())
        .toList();
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiError(messages));
  }
}
