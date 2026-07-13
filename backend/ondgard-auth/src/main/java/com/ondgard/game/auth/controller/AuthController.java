package com.ondgard.game.auth.controller;

import com.ondgard.game.auth.contract.*;
import com.ondgard.game.auth.contract.google.GoogleAuthRequest;
import com.ondgard.game.auth.contract.google.GoogleAuthResult;
import com.ondgard.game.auth.service.AuthService;
import com.ondgard.game.auth.service.GoogleAuthService;
import com.ondgard.game.auth.service.PasswordResetService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping( "/api/auth" )
@RequiredArgsConstructor
@Validated
public class AuthController {

  private final AuthService authService;
  private final GoogleAuthService googleAuthService;
  private final PasswordResetService passwordResetService;

  @PostMapping( "/login" )
  public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
    LoginResponse response = authService.login(request);
    return ResponseEntity.ok(response);
  }

  @PostMapping( "/login/refresh" )
  public ResponseEntity<LoginResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
    LoginResponse response = authService.refreshToken(request);
    return ResponseEntity.ok(response);
  }

  @GetMapping( "/check-username" )
  public ResponseEntity<Map<String, Boolean>> checkUsername(@RequestParam @Size( min = 3, max = 30 ) String username) {
    boolean available = authService.isUsernameAvailable(username);
    return ResponseEntity.ok(Map.of("available", available));
  }

  @PostMapping( "/register" )
  public ResponseEntity<RegisterResponse> register(
      @Valid @RequestBody RegisterRequest request,
      @RequestHeader( value = "Accept-Language", defaultValue = "en" ) String lang) {
    RegisterResponse response = authService.register(request, lang);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @PostMapping( "/confirm" )
  public ResponseEntity<Void> confirmEmail(@Valid @RequestBody ConfirmRequest request) {
    authService.confirmEmail(request.token());
    return ResponseEntity.ok().build();
  }

  @PostMapping( "/resend-confirmation" )
  public ResponseEntity<Void> resendConfirmation(
      @RequestBody @Valid ResendConfirmationRequest request,
      @RequestHeader( value = "Accept-Language", defaultValue = "en" ) String lang) {
    authService.resendConfirmation(request.username(), lang);
    return ResponseEntity.ok().build();
  }

  @PostMapping( "/google" )
  public ResponseEntity<?> googleAuth(@Valid @RequestBody GoogleAuthRequest request) {
    GoogleAuthResult result = googleAuthService.authenticate(request.getCredential(), request.getUsername());
    return switch(result){
      case GoogleAuthResult.LoginSuccess s -> ResponseEntity.ok(s.response());
      case GoogleAuthResult.RegistrationRequired r -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(r.response());
    };
  }

  @PostMapping( "/forgot-password" )
  public ResponseEntity<Void> forgotPassword(
      @RequestBody @Valid ForgotPasswordRequest request,
      @RequestHeader( value = "Accept-Language", defaultValue = "en" ) String lang) {
    passwordResetService.forgotPassword(request.email(), lang);
    return ResponseEntity.ok().build();
  }

  @PostMapping( "/reset-password" )
  public ResponseEntity<Void> resetPassword(@RequestBody @Valid ResetPasswordRequest request) {
    passwordResetService.resetPassword(request.token(), request.newPassword());
    return ResponseEntity.ok().build();
  }
}
