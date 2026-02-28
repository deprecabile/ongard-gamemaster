package com.ongard.game.authentication.controller;

import com.ongard.game.authentication.contract.*;
import com.ongard.game.authentication.service.AuthService;
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
  public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
    RegisterResponse response = authService.register(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }
}
