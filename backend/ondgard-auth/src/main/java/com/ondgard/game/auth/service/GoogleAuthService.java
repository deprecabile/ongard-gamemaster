package com.ondgard.game.auth.service;

import com.ondgard.game.auth.client.ChatClient;
import com.ondgard.game.auth.config.RegistrationProperties;
import com.ondgard.game.auth.contract.LoginResponse;
import com.ondgard.game.auth.contract.google.GoogleAuthResult;
import com.ondgard.game.auth.contract.google.GoogleRegistrationRequiredResponse;
import com.ondgard.game.auth.entity.AppUserEntity;
import com.ondgard.game.auth.model.GoogleUserInfo;
import com.ondgard.game.auth.model.validation.AuthApiErrorCode;
import com.ondgard.game.auth.repository.AppUserRepository;
import com.ondgard.game.auth.repository.EmailConfirmationRepository;
import com.ondgard.game.exception.BadRequestException;
import com.ondgard.game.exception.ForbiddenException;
import com.ondgard.game.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleAuthService {

  private final GoogleTokenVerifier googleTokenVerifier;
  private final AppUserRepository appUserRepository;
  private final EmailConfirmationRepository emailConfirmationRepository;
  private final ChatClient chatClient;
  private final JwtService jwtService;
  private final PasswordService passwordService;
  private final RegistrationProperties registrationProperties;

  @Transactional
  public GoogleAuthResult authenticate(String credential, String username) {
    GoogleUserInfo info = googleTokenVerifier.verify(credential);
    Optional<AppUserEntity> userOpt = appUserRepository.findByEmail(info.email());

    if( userOpt.isPresent() ){
      AppUserEntity user = userOpt.get();
      checkLock(user);

      if( user.getEnabled() ){
        return handleExistingEnabled(user);
      } else{
        return handleExistingDisabled(user);
      }
    }

    // User not found — registration required
    if( !registrationProperties.isEnabled() ){
      throw new ForbiddenException("Registration is currently disabled");
    }

    if( username == null || username.isBlank() ){
      String suggestedUsername = findAvailableUsername(info.name());
      return new GoogleAuthResult.RegistrationRequired(new GoogleRegistrationRequiredResponse(info.email(), suggestedUsername));
    }

    return handleNewUser(info.email(), username);
  }

  private void checkLock(AppUserEntity user) {
    if( user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now()) ){
      throw new UnauthorizedException(AuthApiErrorCode.INVALID_CREDENTIALS, "Invalid credentials");
    }
  }

  private GoogleAuthResult.LoginSuccess handleExistingEnabled(AppUserEntity user) {
    user.setLastLogin(LocalDateTime.now());
    appUserRepository.save(user);
    return new GoogleAuthResult.LoginSuccess(buildLoginResponse(user));
  }

  private GoogleAuthResult.LoginSuccess handleExistingDisabled(AppUserEntity user) {
    chatClient.createUser(user.getUserHash(), user.getUsername());
    user.setEnabled(true);
    emailConfirmationRepository.deleteByUser(user);
    user.setLastLogin(LocalDateTime.now());
    appUserRepository.save(user);
    return new GoogleAuthResult.LoginSuccess(buildLoginResponse(user));
  }

  private GoogleAuthResult.LoginSuccess handleNewUser(String email, String username) {
    if( !appUserRepository.isUsernameAvailable(username) ){
      throw new BadRequestException(AuthApiErrorCode.USERNAME_TAKEN, "Username already taken");
    }

    PasswordService.SaltedHash sh = passwordService.hashRandom();

    AppUserEntity user = AppUserEntity.builder()
        .username(username)
        .email(email)
        .passwordHash(sh.passwordHash())
        .salt(sh.salt())
        .enabled(true)
        .created(LocalDateTime.now())
        .build();

    appUserRepository.save(user);
    user = appUserRepository.findByEmail(email).orElseThrow();

    chatClient.createUser(user.getUserHash(), username);

    return new GoogleAuthResult.LoginSuccess(buildLoginResponse(user));
  }

  private LoginResponse buildLoginResponse(AppUserEntity user) {
    String accessToken = jwtService.generateAccessToken(user);
    String refreshToken = jwtService.generateRefreshToken(user);
    return LoginResponse.builder()
        .accessToken(accessToken)
        .refreshToken(refreshToken)
        .expiresIn(jwtService.getAccessTokenExpiration())
        .build();
  }

  private String findAvailableUsername(String name) {
    String base = sanitizeUsername(name);
    if( appUserRepository.isUsernameAvailable(base) ) return base;

    for( int i = 1; i <= 99; i++ ){
      String suffix = String.valueOf(i);
      String candidate = base.length() + suffix.length() > 30
          ? base.substring(0, 30 - suffix.length()) + suffix
          : base + suffix;
      if( appUserRepository.isUsernameAvailable(candidate) ) return candidate;
    }
    return base;
  }

  private static String sanitizeUsername(String name) {
    if( name == null || name.isBlank() ) return "user";
    String sanitized = name.replaceAll("[^a-zA-Z0-9_-]", "");
    if( sanitized.length() > 30 ) sanitized = sanitized.substring(0, 30);
    if( sanitized.length() < 3 ) sanitized = sanitized + "user".substring(0, Math.min(4, 3 - sanitized.length()));
    return sanitized;
  }
}
