package com.ongard.game.authentication.service;

import com.ongard.game.authentication.client.ChatClient;
import com.ongard.game.authentication.contract.*;
import com.ongard.game.authentication.entity.AppUserEntity;
import com.ongard.game.authentication.model.validation.AuthApiErrorCode;
import com.ongard.game.authentication.repository.AppUserRepository;
import com.ongard.game.exception.BadRequestException;
import com.ongard.game.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class AuthService {

  private static final String PEPPER = "XkP7$mQ2nW";
  private static final int SALT_LENGTH = 32;

  private final AppUserRepository appUserRepository;
  private final Argon2PasswordEncoder passwordEncoder;
  private final ChatClient chatClient;
  private final JwtService jwtService;

  public LoginResponse login(LoginRequest request) {
    AppUserEntity user = appUserRepository.findByUsername(request.getUsername())
        .orElseThrow(() -> new UnauthorizedException(AuthApiErrorCode.INVALID_CREDENTIALS, "Invalid credentials"));

    if( !user.getEnabled() ){
      throw new UnauthorizedException(AuthApiErrorCode.INVALID_CREDENTIALS, "Invalid credentials");
    }

    if( user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now()) ){
      throw new UnauthorizedException(AuthApiErrorCode.INVALID_CREDENTIALS, "Invalid credentials");
    }

    if( !passwordEncoder.matches(PEPPER + request.getPassword() + user.getSalt(), user.getPasswordHash()) ){
      throw new UnauthorizedException(AuthApiErrorCode.INVALID_CREDENTIALS, "Invalid credentials");
    }

    user.setLastLogin(LocalDateTime.now());
    appUserRepository.save(user);

    String accessToken = jwtService.generateAccessToken(user);
    String refreshToken = jwtService.generateRefreshToken(user);

    return LoginResponse.builder()
        .accessToken(accessToken)
        .refreshToken(refreshToken)
        .expiresIn(jwtService.getAccessTokenExpiration())
        .build();
  }

  public LoginResponse refreshToken(RefreshTokenRequest request) {
    return jwtService.rotateRefreshToken(request.getRefreshToken(), request.getUsername());
  }

  public boolean isUsernameAvailable(String username) {
    return !appUserRepository.existsByUsername(username);
  }

  public RegisterResponse register(RegisterRequest request) {
    if( appUserRepository.existsByUsername(request.getUsername()) ){
      throw new BadRequestException(AuthApiErrorCode.USERNAME_TAKEN, "Username already taken");
    }
    if( appUserRepository.existsByEmail(request.getEmail()) ){
      throw new BadRequestException(AuthApiErrorCode.EMAIL_TAKEN, "Email already taken");
    }

    final byte[] saltBytes = new byte[SALT_LENGTH];
    new SecureRandom().nextBytes(saltBytes);
    final String salt = Base64.getEncoder().encodeToString(saltBytes);
    final AppUserEntity user = AppUserEntity.builder()
        .username(request.getUsername())
        .email(request.getEmail())
        .passwordHash(passwordEncoder.encode(PEPPER + request.getPassword() + salt))
        .salt(salt)
        .enabled(true)
        .created(LocalDateTime.now())
        .build();

    final AppUserEntity saved = appUserRepository.save(user);

    chatClient.createUser(saved.getUserHash(), saved.getUsername());

    return RegisterResponse.builder()
        .userHash(saved.getUserHash())
        .username(saved.getUsername())
        .email(saved.getEmail())
        .created(saved.getCreated())
        .build();
  }
}
