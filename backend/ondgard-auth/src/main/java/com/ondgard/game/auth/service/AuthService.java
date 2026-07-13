package com.ondgard.game.auth.service;

import com.ondgard.game.auth.client.ChatClient;
import com.ondgard.game.auth.client.MailClient;
import com.ondgard.game.auth.config.FrontendProperties;
import com.ondgard.game.auth.config.RegistrationProperties;
import com.ondgard.game.auth.contract.*;
import com.ondgard.game.auth.entity.AppUserEntity;
import com.ondgard.game.auth.entity.EmailConfirmationEntity;
import com.ondgard.game.auth.model.validation.AuthApiErrorCode;
import com.ondgard.game.auth.repository.AppUserRepository;
import com.ondgard.game.auth.repository.EmailConfirmationRepository;
import com.ondgard.game.auth.util.TokenHashUtil;
import com.ondgard.game.contract.mail.SendMailRequest;
import com.ondgard.game.exception.BadRequestException;
import com.ondgard.game.exception.ForbiddenException;
import com.ondgard.game.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

  private static final Map<String, String> CONFIRMATION_SUBJECTS = Map.of(
      "en", "Confirm your Ondgard registration",
      "it", "Conferma la tua registrazione su Ondgard",
      "es", "Confirma tu registro en Ondgard"
  );
  private static final Map<String, String> ALREADY_REGISTERED_SUBJECTS = Map.of(
      "en", "Ondgard registration attempt",
      "it", "Tentativo di registrazione su Ondgard",
      "es", "Intento de registro en Ondgard"
  );
  private final AppUserRepository appUserRepository;
  private final EmailConfirmationRepository emailConfirmationRepository;
  private final PasswordService passwordService;
  private final ChatClient chatClient;
  private final JwtService jwtService;
  private final MailClient mailClient;
  private final EmailTemplateService emailTemplateService;
  private final FrontendProperties frontendProperties;
  private final RegistrationProperties registrationProperties;

  public LoginResponse login(LoginRequest request) {
    AppUserEntity user = appUserRepository.findByUsername(request.getUsername()).orElseThrow(() -> new UnauthorizedException(AuthApiErrorCode.INVALID_CREDENTIALS, "Invalid credentials"));

    if( !user.getEnabled() ){
      throw new ForbiddenException(AuthApiErrorCode.ACCOUNT_NOT_CONFIRMED, "Account not confirmed");
    }

    if( user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now()) ){
      throw new UnauthorizedException(AuthApiErrorCode.INVALID_CREDENTIALS, "Invalid credentials");
    }

    if( !passwordService.matches(request.getPassword(), user.getSalt(), user.getPasswordHash()) ){
      throw new UnauthorizedException(AuthApiErrorCode.INVALID_CREDENTIALS, "Invalid credentials");
    }

    user.setLastLogin(LocalDateTime.now());
    appUserRepository.save(user);

    String accessToken = jwtService.generateAccessToken(user);
    String refreshToken = jwtService.generateRefreshToken(user);

    return LoginResponse.builder().accessToken(accessToken).refreshToken(refreshToken).expiresIn(jwtService.getAccessTokenExpiration()).build();
  }

  public LoginResponse refreshToken(RefreshTokenRequest request) {
    return jwtService.rotateRefreshToken(request.getRefreshToken(), request.getUsername());
  }

  public boolean isUsernameAvailable(String username) {
    return appUserRepository.isUsernameAvailable(username);
  }

  @Transactional
  public RegisterResponse register(RegisterRequest request, String lang) {
    if( !registrationProperties.isEnabled() ){
      throw new ForbiddenException("Registration is currently disabled");
    }

    // 1. Check by email
    Optional<AppUserEntity> byEmail = appUserRepository.findByEmail(request.getEmail());
    if( byEmail.isPresent() ){
      AppUserEntity existing = byEmail.get();
      if( existing.getEnabled() ){
        handleAlreadyConfirmed(existing, lang);
      } else{
        handleUnconfirmedByEmail(existing, request, lang);
      }
      return new RegisterResponse(true);
    }

    // 2. Check by username
    Optional<AppUserEntity> byUsername = appUserRepository.findByUsername(request.getUsername());
    if( byUsername.isPresent() ){
      AppUserEntity existing = byUsername.get();
      if( isEffectivelyActive(existing) ){
        throw new BadRequestException(AuthApiErrorCode.USERNAME_TAKEN, "Username already taken");
      }
      // Scenario (b): stale user, delete and proceed as (a)
      emailConfirmationRepository.deleteByUser(existing);
      appUserRepository.delete(existing);
      appUserRepository.flush();
    }

    // Scenario (a): create new user
    createNewUser(request, lang);
    return new RegisterResponse(true);
  }

  public void confirmEmail(UUID token) {
    String tokenHash = TokenHashUtil.sha256Hex(token.toString());
    EmailConfirmationEntity confirmation = emailConfirmationRepository.findByTokenHash(tokenHash)
        .orElseThrow(() -> new BadRequestException(AuthApiErrorCode.INVALID_CONFIRMATION_TOKEN, "Invalid or expired token"));

    if( confirmation.getExpiry().isBefore(LocalDateTime.now()) ){
      throw new BadRequestException(AuthApiErrorCode.INVALID_CONFIRMATION_TOKEN, "Invalid or expired token");
    }

    AppUserEntity user = confirmation.getUser();

    if( user.getEnabled() ){
      return;
    }

    chatClient.createUser(user.getUserHash(), user.getUsername());

    user.setEnabled(true);
    appUserRepository.save(user);

    emailConfirmationRepository.delete(confirmation);
  }

  /**
   * Scenario (c): email gia' usata da utente confermato.
   */
  private void handleAlreadyConfirmed(AppUserEntity existing, String lang) {
    if( existing.getLastEmailSentAt() != null
        && existing.getLastEmailSentAt().isAfter(LocalDateTime.now().minusHours(1)) ){
      return;
    }
    String loginUrl = frontendProperties.getUrl() + "/login";
    String html = emailTemplateService.render(lang, "already-registered",
        Map.of("username", existing.getUsername(), "loginUrl", loginUrl));
    String subject = ALREADY_REGISTERED_SUBJECTS.getOrDefault(lang, ALREADY_REGISTERED_SUBJECTS.get("en"));
    mailClient.sendEmail(new SendMailRequest(existing.getEmail(), subject, html));
    existing.setLastEmailSentAt(LocalDateTime.now());
    appUserRepository.save(existing);
  }

  /**
   * Scenario (d): email gia' usata da non confermato.
   */
  private void handleUnconfirmedByEmail(AppUserEntity existing, RegisterRequest request, String lang) {
    Optional<EmailConfirmationEntity> tokenOpt = emailConfirmationRepository.findByUser(existing);

    // Throttle: token generato < 1h fa (expiry > now + 23h)
    if( tokenOpt.isPresent()
        && tokenOpt.get().getExpiry().isAfter(LocalDateTime.now().plusHours(23)) ){
      return;
    }

    // Se username cambiato, verificare disponibilita'
    if( !request.getUsername().equals(existing.getUsername()) ){
      if( !appUserRepository.isUsernameAvailable(request.getUsername()) ){
        throw new BadRequestException(AuthApiErrorCode.USERNAME_TAKEN, "Username already taken");
      }
      // Eliminare eventuale utente stale con lo stesso username (DB unique constraint)
      appUserRepository.findByUsername(request.getUsername()).ifPresent(stale -> {
        emailConfirmationRepository.deleteByUser(stale);
        appUserRepository.delete(stale);
        appUserRepository.flush();
      });
    }

    // Aggiorna credenziali
    PasswordService.SaltedHash sh = passwordService.hash(request.getPassword());
    existing.setUsername(request.getUsername());
    existing.setPasswordHash(sh.passwordHash());
    existing.setSalt(sh.salt());
    appUserRepository.save(existing);

    // Rigenera token conferma
    UUID plainToken = UUID.randomUUID();
    String tokenHash = TokenHashUtil.sha256Hex(plainToken.toString());
    if( tokenOpt.isPresent() ){
      EmailConfirmationEntity ec = tokenOpt.get();
      ec.setTokenHash(tokenHash);
      ec.setExpiry(LocalDateTime.now().plusHours(24));
      emailConfirmationRepository.save(ec);
    } else{
      emailConfirmationRepository.save(EmailConfirmationEntity.builder()
          .tokenHash(tokenHash).user(existing)
          .expiry(LocalDateTime.now().plusHours(24)).build());
    }

    sendConfirmationEmail(existing.getEmail(), existing.getUsername(), plainToken, lang);
  }

  /**
   * Scenario (a): crea nuovo utente + token + invia email.
   */
  private void createNewUser(RegisterRequest request, String lang) {
    PasswordService.SaltedHash sh = passwordService.hash(request.getPassword());

    AppUserEntity user = AppUserEntity.builder()
        .username(request.getUsername())
        .email(request.getEmail())
        .passwordHash(sh.passwordHash())
        .salt(sh.salt())
        .enabled(false)
        .created(LocalDateTime.now())
        .build();
    appUserRepository.save(user);

    UUID plainToken = UUID.randomUUID();
    String tokenHash = TokenHashUtil.sha256Hex(plainToken.toString());
    emailConfirmationRepository.save(EmailConfirmationEntity.builder()
        .tokenHash(tokenHash).user(user)
        .expiry(LocalDateTime.now().plusHours(24)).build());

    sendConfirmationEmail(user.getEmail(), user.getUsername(), plainToken, lang);
  }

  public void resendConfirmation(String username, String lang) {
    Optional<AppUserEntity> userOpt = appUserRepository.findByUsername(username);
    if( userOpt.isEmpty() || userOpt.get().getEnabled() ){
      return;
    }

    AppUserEntity user = userOpt.get();
    Optional<EmailConfirmationEntity> tokenOpt = emailConfirmationRepository.findByUser(user);

    // Throttle: token generato < 1h fa (expiry > now + 23h)
    if( tokenOpt.isPresent()
        && tokenOpt.get().getExpiry().isAfter(LocalDateTime.now().plusHours(23)) ){
      return;
    }

    UUID plainToken = UUID.randomUUID();
    String tokenHash = TokenHashUtil.sha256Hex(plainToken.toString());
    if( tokenOpt.isPresent() ){
      EmailConfirmationEntity ec = tokenOpt.get();
      ec.setTokenHash(tokenHash);
      ec.setExpiry(LocalDateTime.now().plusHours(24));
      emailConfirmationRepository.save(ec);
    } else{
      emailConfirmationRepository.save(EmailConfirmationEntity.builder()
          .tokenHash(tokenHash).user(user)
          .expiry(LocalDateTime.now().plusHours(24)).build());
    }

    sendConfirmationEmail(user.getEmail(), user.getUsername(), plainToken, lang);
  }

  /**
   * Invia email di conferma.
   */
  private void sendConfirmationEmail(String email, String username, UUID token, String lang) {
    String confirmUrl = frontendProperties.getUrl() + "/confirm?token=" + token;
    String html = emailTemplateService.render(lang, "confirmation",
        Map.of("username", username, "confirmUrl", confirmUrl));
    String subject = CONFIRMATION_SUBJECTS.getOrDefault(lang, CONFIRMATION_SUBJECTS.get("en"));
    mailClient.sendEmail(new SendMailRequest(email, subject, html));
  }

  /**
   * Controlla se l'utente e' attivo (enabled O ha token valido).
   */
  private boolean isEffectivelyActive(AppUserEntity user) {
    if( user.getEnabled() ) return true;
    Optional<EmailConfirmationEntity> token = emailConfirmationRepository.findByUser(user);
    return token.isPresent() && !token.get().getExpiry().isBefore(LocalDateTime.now());
  }
}
