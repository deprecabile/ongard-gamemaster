package com.ondgard.game.auth.service;

import com.ondgard.game.auth.client.MailClient;
import com.ondgard.game.auth.config.FrontendProperties;
import com.ondgard.game.auth.entity.AppUserEntity;
import com.ondgard.game.auth.entity.PasswordResetEntity;
import com.ondgard.game.auth.model.validation.AuthApiErrorCode;
import com.ondgard.game.auth.repository.AppUserRepository;
import com.ondgard.game.auth.repository.PasswordResetRepository;
import com.ondgard.game.auth.repository.RefreshTokenRepository;
import com.ondgard.game.auth.util.TokenHashUtil;
import com.ondgard.game.contract.mail.SendMailRequest;
import com.ondgard.game.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PasswordResetService {

  private static final Map<String, String> PASSWORD_RESET_SUBJECTS = Map.of(
      "en", "Reset your Ondgard password",
      "it", "Reimposta la tua password di Ondgard",
      "es", "Restablece tu contraseña de Ondgard"
  );

  private final AppUserRepository appUserRepository;
  private final PasswordResetRepository passwordResetRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final PasswordService passwordService;
  private final MailClient mailClient;
  private final EmailTemplateService emailTemplateService;
  private final FrontendProperties frontendProperties;

  @Transactional
  public void forgotPassword(String email, String lang) {
    Optional<AppUserEntity> userOpt = appUserRepository.findByEmail(email);
    if( userOpt.isEmpty() || !userOpt.get().getEnabled() ){
      return;
    }

    AppUserEntity user = userOpt.get();

    // Throttle: last email sent < 1h ago
    if( user.getLastEmailSentAt() != null
        && user.getLastEmailSentAt().isAfter(LocalDateTime.now().minusHours(1)) ){
      return;
    }

    UUID plainToken = UUID.randomUUID();
    String tokenHash = TokenHashUtil.sha256Hex(plainToken.toString());

    Optional<PasswordResetEntity> existing = passwordResetRepository.findByUser(user);
    if( existing.isPresent() ){
      PasswordResetEntity pr = existing.get();
      pr.setTokenHash(tokenHash);
      pr.setExpiry(LocalDateTime.now().plusHours(1));
      passwordResetRepository.save(pr);
    } else{
      passwordResetRepository.save(PasswordResetEntity.builder()
          .tokenHash(tokenHash).user(user)
          .expiry(LocalDateTime.now().plusHours(1)).build());
    }

    user.setLastEmailSentAt(LocalDateTime.now());
    appUserRepository.save(user);

    sendPasswordResetEmail(user.getEmail(), user.getUsername(), plainToken, lang);
  }

  @Transactional
  public void resetPassword(UUID token, String newPassword) {
    String tokenHash = TokenHashUtil.sha256Hex(token.toString());
    PasswordResetEntity resetEntity = passwordResetRepository.findByTokenHash(tokenHash)
        .orElseThrow(() -> new BadRequestException(AuthApiErrorCode.INVALID_RESET_TOKEN, "Invalid or expired reset token"));

    if( resetEntity.getExpiry().isBefore(LocalDateTime.now()) ){
      throw new BadRequestException(AuthApiErrorCode.INVALID_RESET_TOKEN, "Invalid or expired reset token");
    }

    AppUserEntity user = resetEntity.getUser();

    PasswordService.SaltedHash sh = passwordService.hash(newPassword);
    user.setPasswordHash(sh.passwordHash());
    user.setSalt(sh.salt());
    appUserRepository.save(user);

    refreshTokenRepository.revokeByUser(user);
    passwordResetRepository.delete(resetEntity);
  }

  private void sendPasswordResetEmail(String email, String username, UUID token, String lang) {
    String resetUrl = frontendProperties.getUrl() + "/reset-password?token=" + token;
    String html = emailTemplateService.render(lang, "password-reset",
        Map.of("username", username, "resetUrl", resetUrl));
    String subject = PASSWORD_RESET_SUBJECTS.getOrDefault(lang, PASSWORD_RESET_SUBJECTS.get("en"));
    mailClient.sendEmail(new SendMailRequest(email, subject, html));
  }
}
